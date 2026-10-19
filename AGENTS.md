# Project instructions

MiniSearch is a public, open-source Java document search engine. Keep the code
clear, maintainable, and focused. Use concise comments and documentation.

- Discuss and scope each stage before implementation; begin only after approval.
- Use `feat/descriptive-name` for features and `fix/descriptive-name` for fixes.
- Make focused, meaningful commits with short lowercase subjects. Commit counts
  are flexible targets: roughly 50 for small or medium changes and 100 for major
  phases when the scope warrants them. Never split or add work just to reach a count.
- Credit `nasho-x` selectively on critical implementation changes and important
  fixes using `Co-authored-by: nasho-x <174001652+nasho-x@users.noreply.github.com>`.
- Do not add assistant or tool credits, signatures, generated-by notices, or
  assistant co-author entries anywhere in the repository or Git metadata.
- Do not run Docker, database commands or migrations, the full final test suite,
  benchmarks, or Git push. Focused offline checks are allowed.
- Maintain one verification script that runs required setup, checks, tests, and
  applicable benchmarks in order, reports progress, and stops on failure.
- At the end of each stage, report changes, branch, commit count, checks run,
  remaining limitations, and the single command for the user to run. Clearly
  distinguish checks run locally from those awaiting user execution.
