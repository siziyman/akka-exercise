# Akka test task
Test task. Also first experience with Akka and actor model/paradigm in general.
### Arguments:
`-p <number>` - port for the app to listen on. Mandatory.
`-n <names list>` - comma-separated list of names (to spawn according actors). Optional. Default names are John, Alex, Juan, Alejandro.
`-s <statuses list>` - comma-separated list of statuses. Optional. Default statuses are "asleep", "awake".

## Build
Run `sbt assembly` in project root.
