# Proposal: approvals for schema changes

Add `ApprovalService` and an `approvals` store to `exeris-platform-lsp`. Before
`exeris/applyMutation` writes to disk, the server checks whether the mutation needs an approver; if
so, the mutation is queued until a user with the `domain-approver` role approves it through a new
`exeris/approveMutation` method. Roles are assigned per organisation in a new `org_roles` store.
