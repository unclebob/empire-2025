# Refactor: DRY up a-star / bounded-a-star duplication

## Problem
`bounded-a-star` duplicates ~90% of `a-star`. Both have high CRAP scores (15.6 and 15.1) due to the combined effect of CC 8-9 and ~55% coverage.

## Design
Add an optional `neighbor-filter` parameter to `a-star`. `bounded-a-star` computes its bounding box and delegates.

### Step 1: Modify `a-star`
- Add 3rd arity: `[start goal unit-type game-map passability-fn neighbor-filter]`
- Chain 2-arity → 3-arity with nils
- After `(remove closed-set neighbors)`, apply `neighbor-filter` when non-nil

### Step 2: Simplify `bounded-a-star`
- Keep bounding-box computation (midpoint + radius + `in-bounds?`)
- Delegate to `(a-star start goal unit-type game-map nil in-bounds?)`
- Delete duplicated loop body (~45 lines)

## Expected impact
- `bounded-a-star`: CC 9 → ~2, CRAP 15.6 → ~2
- `a-star`: CC unchanged (filter is in existing branch)
- All callers unchanged
- Net ~45 lines removed
