# Proposal: a promotion hook

Add a `PromotionTarget` interface to `exeris-studio-backend`, discovered with `ServiceLoader`, with a
single method `PromotionResult promote(WorkspaceSnapshot snapshot)`. The open-core distribution
ships no implementation and Studio hides the "Promote" button when none is found. The interface,
its Javadoc and a test asserting the button is hidden without a provider are the whole change.
