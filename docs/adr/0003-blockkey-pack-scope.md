# BlockKey.pack is only for the shared 26/12/26 biased-Y encoding

`BlockKey.pack` is the single home for the coordinate encoding that is byte-identical between
the shared block utilities and its test. The similarly named `ArenaBaseline.pack` and
`DomainRegistry.pack` remain separate: they encode coordinates for different owners (persisted
arena baseline vs. live domain claims), so collapsing them would couple unrelated contexts and
one of them is persisted data whose format must stay stable.

_Considered Options_: one global coordinate packer was rejected because the three methods carry
different ownership and persistence guarantees despite a similar bit layout.
