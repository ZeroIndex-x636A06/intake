# Program: Strength Block A

<!-- A generic example. Not anyone's real program. -->

units: lb
rest: 2:00
progress: double progression, add 5 lb
notes: Four weeks, then retest. Warm up with two lighter sets before the first lift.

## Upper A
note: Keep the first two lifts heavy and the rest light.

### Bench Press
sets: 4
reps: 6-8
rpe: 8
weight: 185
rest: 2:30
note: Pause one second on the chest.

### Barbell Row
sets: 4
reps: 8 to 10
weight: 135 lb

### Overhead Press
sets: 3
reps: 5
weight: 95
progress: linear, add 5 lb, deload 10% after 3 misses

### Pull-up
sets: 3
reps: max
weight: bodyweight
progress: none

### Dumbbell Curl
sets: 3
reps: 10-12
weight: 30
progress: double, add 5

## Lower A

### Back Squat
sets: 3
reps: 5
weight: 225
rest: 3:00
progress: linear, add 10 lb, deload 10% after 3 misses

### Romanian Deadlift
sets: 3
reps: 8-10
rpe: 7.5
weight: 185
progress: rpe

### Leg Press
sets: 3
reps: 10-12
weight: 270
progress: 75% of max

### Box Jump
4x3
rest: 90 seconds
progress: none

### Weighted Dip
sets: 3
reps: 8-10
weight: bodyweight + 25 lb
progress: custom: if (completedReps >= reps * sets) weight + 2.5 else weight

## Upper B

### Incline Dumbbell Press
sets: 3
reps: 8-12
weight: 60 lb
rest: 2 min 30 s

### Lat Pulldown
sets: 3
reps: 10-12
weight: 120
