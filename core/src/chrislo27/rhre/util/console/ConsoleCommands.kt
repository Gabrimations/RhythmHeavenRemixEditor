package chrislo27.rhre.util.console

import chrislo27.rhre.Main
import chrislo27.rhre.json.GameObject
import chrislo27.rhre.registry.*
import chrislo27.rhre.util.JsonHandler
import chrislo27.rhre.version.RHRE2Version
import com.badlogic.gdx.Gdx
import java.util.*

object ConsoleCommands {

	fun handle(main: Main, command: String, args: List<String>): Boolean {
		val idDumpName: String = "idDump" + if (args.isNotEmpty() && !args[0].startsWith("-")) "_${args[0]}" else ""

		return when (command.toLowerCase(Locale.ROOT)) {
			"quit", "exit" -> {
				Gdx.app.exit()
				true
			}
			"dumpids" -> {
				val filteredGames = GameRegistry.gameList.filter { it.series != SeriesList.CUSTOM }
				val allIDs: String = JsonHandler.toJson(IDDump(
						filteredGames.map(Game::id),
						filteredGames.flatMap(Game::soundCues).map(SoundCue::id),
						filteredGames.flatMap(Game::patterns).filter { !it.autoGenerated }.map(Pattern::id)
															  ))

				val writeWithoutPrompt: Boolean = args.contains("-w")

				if (!writeWithoutPrompt) {
					println("Write to preferences at $idDumpName? (Y/N)")
				}
				if (writeWithoutPrompt || (readLine() ?: throw IllegalArgumentException("Got null for yes/no")).equals("y", ignoreCase = true)) {
					println("Writing to preferences...")
					main.preferences.putString(idDumpName, allIDs).flush()
					println("Wrote successfully.")
				}

				if (args.contains("-p")) {
					println("\n" + allIDs + "\n\n")
				}

				false
			}
			"checkids" -> {
				val json = main.preferences.getString(idDumpName, null) ?: throw IllegalStateException(
						"Cached ID dump is null")
				val list: IDDump = JsonHandler.fromJson(json)

				println("Checking game list for $idDumpName")
				list.games.forEach {
					if (GameRegistry[it] == null) {
						println("[GAME] Not found: $it")
					}
				}
				println("\nChecking SFX list")
				list.sfx.forEach {
					if (GameRegistry.getCue(it) == null) {
						println("[SFX] Not found: $it")
					}
				}
				println("\nChecking pattern list")
				list.patterns.forEach {
					if (GameRegistry.getPattern(it) == null) {
						println("[PATTERN] Not found: $it")
					}
				}
				println()
				println("Checking for new additions")
				val filteredGames = GameRegistry.gameList.filter { it.series.builtIn }
				filteredGames.map(Game::id).forEach {
					if (!list.games.contains(it)) {
						println("[NEW GAME] $it")
					}
				}
				filteredGames.flatMap(Game::soundCues).map(SoundCue::id).forEach {
					if (!list.sfx.contains(it)) {
						println("[NEW SFX] $it")
					}
				}
				filteredGames.flatMap(Game::patterns).filter { !it.autoGenerated }.map(Pattern::id).forEach {
					if (!list.patterns.contains(it)) {
						println("[NEW PATTERN] $it")
					}
				}

				println("Complete")

				false
			}
			"version" -> {
				val version: RHRE2Version = if (args.isEmpty())
					RHRE2Version.VERSION
				else
					RHRE2Version.fromString(args.first())

				println("$version - ${version.numericalValue}")

				false
			}
			"rename" -> {
				if (args.first() == args[1]) {
					throw IllegalArgumentException("No name change detected")
				}
				rename(args.first(), args[1])
				false
			}
			"help" ,"?" -> {
				println("""Commands:
quit/exit
  - Exits the program.

help/?
  - Shows this help message.

dumpids [name] [-w] [-p]
  - Dumps every game, cue, and pattern ID that isn't auto-generated nor custom.
  - The name is an optional name that doesn't contain spaces nor starts with a hyphen.
  - The -w flag indicates if it should write to a file without prompting.
  - The -p flag indicates if it should print out the json output.

checkids [name]
  - Runs an ID check from persistent data. The name is optional. This will check deletions and additions of IDs. Deprecations are included.

version [version]
  - Shows the numeric form of the version. Defaults to current version.

rename <gameID> <newGameID>
  - Renames a game, adding deprecations where necessary.
""")
				false
			}
			else -> throw IllegalArgumentException("Unknown command \"$command\", use help to view commands")
		}
	}

	private fun rename(original: String, new: String) {
		val obj: GameObject = JsonHandler.fromJson(Gdx.files.local("sounds/cues/$original/data.json").readString("UTF-8"))

		obj.gameID = new
		obj.cues?.forEach { so ->
			so.deprecatedIDs = arrayOf()
			val list = so.deprecatedIDs!!.toMutableList()
			list += so.id!!
			so.deprecatedIDs = list.toTypedArray()

			so.id = new + "/" + so.id!!.substringAfter('/')
		}

		obj.patterns?.forEach { po ->
			po.deprecatedIDs = arrayOf()
			val list = po.deprecatedIDs!!.toMutableList()
			list += po.id!!
			po.deprecatedIDs = list.toTypedArray()

			po.id = new + "_" + po.id!!.substringAfter('_')

			po.cues?.forEach {
				it.id = new + "/" + it.id!!.substringAfter('/')
			}
		}

		println(JsonHandler.toJson(obj))
	}

	private data class IDDump(var games: List<String>, var sfx: List<String>, var patterns: List<String>)

}