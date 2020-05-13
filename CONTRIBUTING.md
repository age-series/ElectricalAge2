# Contributing

Contact us on our Discord at [https://discord.gg/YjK2JAD](https://discord.gg/YjK2JAD)

Feel free to make pull requests or issues, but please note that we are currently focused on making the mod functional, before adding more content.

## PR Guidelines

* Keep PR's to one topic, if possible. This permits us to use version control to find software regressions more easily.
* Avoid mass operations such as folder moves and restructures next to logical changes in the code.
* Use the PR message to describe changes; use markdown check marks to show intended progress towards a goal.
* Keep PR up to date with the `master` branch to ease merges. In the future, once we have one, keep up to date with `develop` instead.
* PR's should come with their code documented and unit tested (if possible).
* If you make a PR, you agree to the licenses of the project, and that the code and content you are contributing complies with the applicable license.

## Branch Naming

Please use concise names that accurately describe what you are doing. For example, `feature/add-variable-resistor` may describe some new Variable Resistor you are adding.

The following naming convention is to be used, with the exception that currently `master` is being used as `develop` (see below comment).

* `master` – Stable release branch that is known for production level stability and has hotfixes applied if that is not the case.
* `develop` – Development branch, with feature branches being created from this branch and merged back into it. Code here should pass standardized testing at least.
* `feature/(branch name)` – Branches created by developers that add new features to the development branch.
* `hotfix/(branch name)` – Branches for urgent fixes against `master` (and are also pushed to `develop` at the same time).

Currently, we use `master` as `develop`, but this will change relatively soon, once we have a working product that can be loaded in a game and connects the games to the engine.

Click [here](https://nvie.com/posts/a-successful-git-branching-model/) for more details on this software development strategy.

## Licensing breakdown

All of the code (Kotlin, Java, Rust, etc) in this repository is [MIT Licensed](LICENSE.md).

All of the content (images, models, etc) in this repository is currently [CC0](https://creativecommons.org/share-your-work/public-domain/cc0/), but [CC BY-SA](https://creativecommons.org/licenses/by-sa/4.0/) has been proposed.

## Useful Resources

* [How to write good Git Commits (and more)](https://chris.beams.io/posts/git-commit/)
* [How to compose good Pull Requests that are likely to be accepted.](https://www.atlassian.com/blog/git/written-unwritten-guide-pull-requests)
* The [Pro Git Book](https://git-scm.com/book/en/v2) - anything you could want to know about Git itself, and how to use the CLI.
* [How to create unit tests](https://www.jetbrains.com/help/idea/create-tests.html)
* [Google Protocol Buffers](https://developers.google.com/protocol-buffers) (Used in network communications, data serialization)
## Code Layout

* apps: Standalone Applications
    * Asm Computer UI
* core: Core Simulation Code
    * compute: Various computer architectures for signal processing and other purposes
    * debug: Debug helpers
    * math: Math helpers
    * parsers: Parsers and import/export tools
    * sim: Electrical and Thermal simulators
    * space: various data structures and algorithms
* images: Images for the falstad importer when exporting circuit data
* integration-mc1-15: Minecraft 1.15.2 Integration Code
* integration-vs: Vintage Story Integration Code
* logcollector: A rust statistics and (crash) logging server
* proto: A set of [Google Protocol Buffers](https://developers.google.com/protocol-buffers) for communication with all of our applications (proto3)
* shared: For code that may be shared amongst platforms (maybe)
