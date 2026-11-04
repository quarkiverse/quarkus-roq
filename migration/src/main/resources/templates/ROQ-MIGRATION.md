{!
  The checklist MigrationReport writes to the converted site, with the report itself as data.
  Values are never parsed, so to-do messages need no escaping. Literal braces in this text would: wrap them in an
  unparsed block with more pipes than they contain.
!}
# Finishing the migration to Roq

The migration from {siteType.displayName} left these for you to do by hand.
Tick them off as you go, and delete this file when you are done.
{#for stage in report.actionItemsByStage.entrySet}

## {stage.key}

{#for message in stage.value}
- [ ] {message}
{/for}
{/for}
