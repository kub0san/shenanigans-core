# Keep cohesive classes whole; split only with evidence of duplicated bodies

Large classes are not split on line count alone. `DomainFloor` (~1208 lines) and
`ClientBlockUtils` (~312 lines) were reviewed and deliberately kept intact: the former's
teardown shares every column array with its build path, the latter already has a clean
`BlockChangeTransport` seam, and neither has duplicated bodies across the proposed boundaries.
The nine single-file packages were likewise kept — each is an actively used utility or entry
point, and folding them into parent packages would be churn with no drift benefit.

_Considered Options_: facade/wrapper splits and package folds were rejected because they would
move whole classes or unrelated utilities without removing duplication.
