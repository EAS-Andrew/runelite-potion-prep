# Potion Prep

A RuneLite plugin for Herblore. Pick a potion in the side panel and the bank shows only that potion's ingredients, lined up in one column (the right-most by default).

The panel shows:

- how many potions you can make now, and how many after prep
- your count of each ingredient (bank + inventory, plus potion storage once the view has been shown)
- what to do next: clean grimy herbs, make unf potions, crush secondaries
- what you're short of, with where to get it, and any Herblore level you're missing

## How it works

It uses the core **Bank Tags** plugin's API: it registers a custom tag and generates a layout for it. The view only changes how the client draws the bank. Items are never moved server-side and nothing is clicked for you.

## Development

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew test        # unit tests for the planner logic
./gradlew runClient   # RuneLite with the plugin loaded, in developer mode
```
