# Proposal: approvals for schema changes

Add `ApprovalService` and an `approvals` table to `exeris-studio-backend`. Before
`exeris/applyMutation` writes to disk, Studio asks the backend whether the mutation needs an
approver; if so, the mutation is queued until a user with the `domain-approver` role approves it in
a new "Pending approvals" screen. Roles are assigned per organisation in a new `org_roles` table.
