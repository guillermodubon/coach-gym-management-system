# Optional Short Prompts

These are conveniences, not required formats. After project re-onboarding, prompts may be written naturally.

## Implement a block

```text
Implement only Block <N>: <name> from docs/branch-plans/<plan>.md. Validate it and stop before the next block.
```

## Continue with the next block

```text
Review the current repository state, then implement only Block <N>: <name> from the active branch plan. Do not redo earlier blocks or start later blocks.
```

## Repair a failure

```text
The authorized scope remains Block <N>. Diagnose and fix the root cause of this failure, rerun the smallest failed test and the block validation, then stop:

<paste failure>
```

## Review without changes

```text
Use $quality-gate-review and $security-and-privacy-review. Review the current block without modifying files. Return a verdict with evidence.
```

## Final branch gate

```text
Use $quality-gate-review. Review the completed branch against the active plan and blueprint, run all required final validation including clean check, and stop without Git mutations.
```
