# Contributing guide

**First off all, thank you for taking the time to contribute into WildFly AI Feature Pack!** The below contents will help you through the steps for getting started with WildFly AI Feature Pack. Please make sure to read the relevant section before making your contribution. It will make it a lot easier for us maintainers and smooth out the experience for all involved. The community looks forward to your contributions.

* Git Setup: https://github.com/wildfly/wildfly/blob/main/docs/src/main/asciidoc/_hacking/github_setup.adoc
* Contributing: https://github.com/wildfly/wildfly/blob/main/docs/src/main/asciidoc/_hacking/contributing.adoc
* Pull request standard: https://github.com/wildfly/wildfly/blob/main/docs/src/main/asciidoc/_hacking/pullrequest_standards.adoc

If you like our project, but just don’t have time to contribute, that’s fine. There are other easy ways to support the project and show your appreciation.

* Mention the project at local meetups and tell your friends/colleagues.
* Tweet about it and also check out our [twitter page](https://twitter.com/WildFlyAS) and [mastodon page](https://fosstodon.org/@wildflyas).
* Check out our [youtube](https://www.youtube.com/@WildFlyAS) contents.

## Forking the Project 
To contribute, you will first need to fork the [wildfly-ai-feature-pack](https://github.com/wildfly/wildfly-ai-feature-pack) repository. 

This can be done by looking in the top-right corner of the repository page and clicking "Fork".

The next step is to clone your newly forked repository onto your local workspace. 
This can be done by going to your newly forked repository, which should be at `https://github.com/USERNAME/wildfly-ai-feature-pack`. 

Then, there will be a green button that says "Code". Click on that and copy the URL.

Then, in your terminal, paste the following command:
```bash
git clone [URL]
```
Be sure to replace [URL] with the URL that you copied.

Now you have the repository on your computer!

## Issues

WildFly AI Feature Pack uses GitHub Issues to manage issues. All issues can be found [here](https://github.com/wildfly/wildfly-ai-feature-pack/issues).

## Setting up your Developer Environment
You will need:

* JDK 11
* Git
* Maven 3.3.9 or later
* An [IDE](https://en.wikipedia.org/wiki/Comparison_of_integrated_development_environments#Java)
(e.g., [Apache NetBeans](https://netbeans.apache.org/front/main/download/), [Eclipse](https://www.eclipse.org/downloads/), [IntelliJ IDEA](https://www.jetbrains.com/idea/download/), etc.)

First `cd` to the directory where you cloned the project (eg: `cd wildfly-datasources-galleon-pack`)

Add a remote ref to upstream, for pulling future updates.
For example:

```
git remote add upstream https://github.com/wildfly/wildfly-ai-feature-pack
```
To build `wildfly-ai-feature-pack` run:
```bash
mvn clean install
```

To skip the tests, use:

```bash
mvn clean install -DskipTests=true
```

To run only a specific test, use:

```bash
mvn clean install -Dtest=TestClassName
```
## Contributing Guidelines

When submitting a PR, please keep the following guidelines in mind:

1. In general, it's good practice to squash all of your commits into a single commit. For larger changes, it's ok to have multiple meaningful commits. If you need help with squashing your commits, feel free to ask us how to do this on your pull request. We're more than happy to help!

2. Please include the GitHub issue you worked on in the title of your pull request and in your commit message. 

3. Please include the link to the GitHub issue you worked on in the description of the pull request.

Lastly, this project is an open source project. Please act responsibly, be nice, polite and enjoy!