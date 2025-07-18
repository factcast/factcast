<!-- omit in toc -->

# Contributing to FactCast

First off, thanks for taking the time to contribute! ❤️

All types of contributions are encouraged and valued. See the [Table of Contents](#table-of-contents) for different ways
to help and details about how this project handles them. Please make sure to read the relevant section before making
your contribution. It will make it a lot easier for us maintainers and smooth out the experience for all involved. The
community looks forward to your contributions. 🎉

> And if you like the project, but just don't have time to contribute, that's fine. There are other easy ways to support
> the project and show your appreciation, which we would also be very happy about:
>
> - Star the project
> - Toot about it
> - Refer this project in your project's readme
> - Mention the project at local meetups and tell your friends/colleagues

<!-- omit in toc -->

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [I Have a Question](#i-have-a-question)
- [I Want To Contribute](#i-want-to-contribute)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Enhancements](#suggesting-enhancements)
  - [Your First Code Contribution](#your-first-code-contribution)
  - [Improving The Documentation](#improving-the-documentation)
- [Styleguides](#styleguides)
  - [Branch names](#branch-names)
  - [Commit Messages](#commit-messages)
- [Join The Project Team](#join-the-project-team)
- [Publishing](#publishing)

## Code of Conduct

This project and everyone participating in it is governed by the
[FactCast Code of Conduct](https://github.com/factcast/factcast/blob/main/CODE_OF_CONDUCT.md).
By participating, you are expected to uphold this code. Please report unacceptable behavior
to <contact@factcast.org>.

## I Have a Question

> If you want to ask a question, we assume that you have read the available [Documentation](https://docs.factcast.org).

Before you ask a question, it is best to search for existing [Issues](https://github.com/factcast/factcast/issues) that
might help you. In case you have found a suitable issue and still need clarification, you can write your question in
this issue. It is also advisable to search the internet for answers first.

If you then still feel the need to ask a question and need clarification, we recommend the following:

- Open an [Issue](https://github.com/factcast/factcast/issues/new).
- Provide as much context as you can about what you're running into.
- Provide project and platform versions (jvm, os, etc), depending on what seems relevant.
- Tag the issue with the label "question"

We will then take care of the issue as soon as possible.

If you want to try a direct conversation, you might be lucky finding a developer
on [Gitter](https://gitter.im/factcast/community)

## I Want To Contribute

> ### Legal Notice <!-- omit in toc -->
>
> When contributing to this project, you must agree that you have authored 100% of the content, that you have the
> necessary rights to the content and that the content you contribute may be provided under the project license.

### Reporting Bugs

<!-- omit in toc -->

#### Before Submitting a Bug Report

A good bug report shouldn't leave others needing to chase you up for more information. Therefore, we ask you to
investigate carefully, collect information and describe the issue in detail in your report. Please complete the
following steps in advance to help us fix any potential bug as fast as possible.

- Make sure that you are using the latest version.
- Determine if your bug is really a bug and not an error on your side e.g. using incompatible environment
  components/versions (Make sure that you have read the [documentation](https://docs.factcast.org). If you are looking
  for support, you might want to check [this section](#i-have-a-question)).
- To see if other users have experienced (and potentially already solved) the same issue you are having, check if there
  is not already a bug report existing for your bug or error in
  the [bug tracker](https://github.com/factcast/factcast/issues?q=label%3Abug).
- Also make sure to search the internet (including Stack Overflow) to see if users outside of the GitHub community have
  discussed the issue.
- Collect information about the bug:
  - Stack trace (Traceback)
  - OS, Platform and Version (Windows, Linux, macOS, x86, ARM)
  - Version and flavor of the JVM
  - Possibly your input and the output
  - Can you reliably reproduce the issue? And can you also reproduce it with older versions?

<!-- omit in toc -->

#### How Do I Submit a Good Bug Report?

> You must never report security related issues, vulnerabilities or bugs including sensitive information to the issue
> tracker, or elsewhere in public. Instead, sensitive bugs must be sent by email to <security@factcast.org>.

<!-- You may add a PGP key to allow the messages to be sent encrypted as well. -->

We use GitHub issues to track bugs and errors. If you run into an issue with the project:

- Open an [Issue](https://github.com/factcast/factcast/issues/new). (Since we can't be sure at this point whether it is
  a bug or not, we ask you not to talk about a bug yet and not to label the issue as such.)
- Explain the behavior you would expect and the actual behavior.
- Please provide as much context as possible and describe the _reproduction steps_ that someone else can follow to
  recreate the issue on their own. This usually includes your code. For good bug reports you should isolate the problem
  and create a reduced test case.
- Provide the information you collected in the previous section.

Once it's filed:

- The project team will label the issue accordingly.
- A team member will try to reproduce the issue with your provided steps. If there are no reproduction steps or no
  obvious way to reproduce the issue, the team will ask you for those steps and mark the issue as `needs-repro`. Bugs
  with the `needs-repro` tag will not be addressed until they are reproduced.
- If the team is able to reproduce the issue, it will be marked `needs-fix`, as well as possibly other tags (such as
  `critical`), and the issue will be left to be [implemented by someone](#your-first-code-contribution).

### Suggesting Enhancements

This section guides you through submitting an enhancement suggestion for FactCast, **including completely new features
and minor improvements to existing functionality**. Following these guidelines will help maintainers and the community
to understand your suggestion and find related suggestions.

<!-- omit in toc -->

#### Before Submitting an Enhancement

- Make sure that you are using the latest version.
- Read the [documentation](https://docs.factcast.org) carefully and find out if the functionality is already covered,
  maybe by an individual configuration.
- Perform a [search](https://github.com/factcast/factcast/issues) to see if the enhancement has already been suggested.
  If it has, add a comment to the existing issue instead of opening a new one.
- Find out whether your idea fits with the scope and aims of the project. It's up to you to make a strong case to
  convince the project's developers of the merits of this feature. Keep in mind that we want features that will be
  useful to the majority of our users and not just a small subset. If you're just targeting a minority of users,
  consider writing an add-on/plugin library.

<!-- omit in toc -->

#### How Do I Submit a Good Enhancement Suggestion?

Enhancement suggestions are tracked as [GitHub issues](https://github.com/factcast/factcast/issues).

- Use a **clear and descriptive title** for the issue to identify the suggestion.
- Provide a **step-by-step description of the suggested enhancement** in as many details as possible.
- **Describe the current behavior** and **explain which behavior you expected to see instead** and why. At this point
  you can also tell which alternatives do not work for you.
- **Explain why this enhancement would be useful** to most FactCast users. You may also want to point out the other
  projects that solved it better and which could serve as inspiration.

<!-- You might want to create an issue template for enhancement suggestions that can be used as a guide and that defines the structure of the information to be included. If you do so, reference it here in the description. -->

### Your First Code Contribution

When committing code to the repository it should be formatted according to our guidelines. You can do this locally
before committing by manually running

```bash
./bash format.sh
```

or

```bash
./mvnw spotless:apply --non-recursive
```

in the project's root folder.

Please note, that if you do not format your commit, a GitHub action will :D

### Using git hooks

If using a linux or mac, you can run

```bash
bash use_local_hooks.sh
```

in the project root folder once in order to configure git to use the hooks provided within this repository.

### Improving The Documentation

If you want to change any content of a page in the docs, there is a link in the upper right that takes you to the page
on github and lets you edit it directly.

In case you want to change the markdown locally, maybe rearrange or create pages, you can edit the markdown within
factcast-site/documentation-docsy/content. If you want to see you changes, feel free to use `bash serve.sh` in there
to fire up a local server and inspect your changes.

## Styleguides

### Branch names

We like the branch names to be prefixed with 'issueXYZ', where XYZ is the id of the issue this branch is supposed to
contribute to.

### Commit Messages

We like the issue id prefixing the commit message. Local hooks might help you with that, given that you use the above
branch name pattern.

## Join The Project Team

You can be invited to join the team after a few contributions. Please contact the project developers/maintainers/admins.

## Publishing

If you are a project team member and have the permission to publish factcast artifacts to maven central, make sure you
set up the local credentials like described here:

https://central.sonatype.org/publish/publish-portal-maven/

To actually publish, for snapshots just do

```shell
mvn clean deploy -Dcentral
```

for releases, you'd want to

```shell
mvn gitflow:release -B
git co <release version>
mvn clean deploy -Dcentral
```

<!-- omit in toc -->

#### Attribution

This guide is based on the **contributing-gen**. [Make your own](https://github.com/bttger/contributing-gen)!
