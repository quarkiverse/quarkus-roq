# Contributing guide

**Want to contribute? Great!** We try to make it easy, and all contributions, even the smaller ones, are more than welcome. This includes bug reports, fixes, documentation, examples... But first, read this page.

Roq is part of the Quarkus ecosystem, contributions should follow the same principles when applicable (https://github.com/quarkusio/quarkus/blob/main/CONTRIBUTING.md).

## Reporting an issue

This project uses GitHub issues to manage the issues. Open an issue directly in GitHub.

If you believe you found a bug, and it's likely possible, please indicate a way to reproduce it, what you are seeing, and
what you would expect to see. Don't forget to indicate your Quarkus, Java, Maven/Gradle, and GraalVM versions.

## Tests and documentation are not optional

Don't forget to include tests in your pull requests. Also don't forget the documentation (reference documentation, javadoc, etc.).

## LLM Usage Policy

See https://github.com/quarkusio/quarkus/blob/main/CONTRIBUTING.md#llm-usage-policy


### Running

This project uses Java 21 and [Maven](https://maven.apache.org/) as build tooling.

To run the tests, use the following:

```shell
mvn verify
```

### Code Style

Maven automatically formats code and organizes imports when you run `mvn verify`. So, we recommend you do that before sending your PR. Otherwise, PR checks will fail.

## Installing Roq extensions

To install all Roq extensions, we need to run:

```shell
mvn clean install
```

## Running Blog

There are two ways for running the blog post locally:

- **With live reload:** Great when you are writing your blog.
- **Serving your static site:** When you want to see how your blog looks, before the deployment.

### Running with live reload

Go to `blog` directory:

```shell
cd blog
```

And, to execute the following maven command:

```shell
mvn quarkus:dev
```

If you are using Quarkus CLI, you can use:

```shell
quarkus dev
```

Now, you can access: http://localhost:8080 and be happy!

### Serving your static site

See our documentation to see [how to generate your static files](https://docs.quarkiverse.io/quarkus-roq/dev/quarkus-roq-generator.html#_generating_your_static_site).

## For the maintainers

### Backlog

We have a [Kanban board](https://github.com/orgs/quarkiverse/projects/6), which is currently visible only by members of the [Quarkiverse organization](https://github.com/quarkiverse).
