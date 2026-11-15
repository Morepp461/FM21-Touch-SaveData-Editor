# FMRTE 21.4.0 archive analysis

Date: 15 November 2026

## Source examined

The supplied `FMRTE_21.4.0.40.zip` archive was inspected locally. It contains 670 entries, including `FMRTE.exe`, `miniFMRTE.exe`, the English localization, `miniFMRTE/players.xml`, `miniFMRTE/staff.xml`, `miniFMRTE/clubs.xml`, and `Ratings/FMRTE.json`.

This is a reference inventory only. The archive is a Windows FMRTE 21 application, not an FM21 Touch Android save-format specification. No executable code, proprietary UI assets, or binary database payloads are copied into this repository.

## Player fields exposed by the MiniFMRTE layout

The supplied `miniFMRTE/players.xml` declares these player columns:

| Attribute | FMRTE layout access |
|---|---|
| DisplayedName | Read-only |
| CA | Editable |
| Age | Read-only |
| PlayerRating.ActualRating | Read-only |
| PA | Editable |
| PlayerRating.PotentialRating | Read-only |
| Morale | Editable |
| Condition | Editable |
| IsRegen | Read-only |
| WorldReputation | Editable |

The staff layout exposes DisplayedName and ID as read-only. The clubs layout contains only a basic position entry and does not provide a field schema.

These declarations confirm that FMRTE 21 presents CA, PA, morale, condition, and world reputation as editable player properties in its own workflow. They do not identify the corresponding FM21 Touch `.fmv` byte offsets, record owners, encoding, scale, or persistence behavior.

## Rating metadata inventory

`Ratings/FMRTE.json` contains ten positional rating profiles:

- Goalkeeper
- DefenderCentral
- FullBack
- WingBack
- DefensiveMidfielder
- MidfielderCentral
- WideMidfielder
- AttackingMidfielderCentral
- Winger
- Striker

Each profile includes an age-peak value and weights for the rating categories. The shared category names are:

- Goalkeeper: AerialAbility, CommandOfArea, Communication, Eccentricity, Handling, Kicking, OneOnOnes, Reflexes, RushingOut, TendencyToPunch, Throwing.
- Mental: Aggression, Anticipation, Bravery, Composure, Concentration, Decisions, Determination, Flair, Leadership, OffTheBall, Positioning, Teamwork, Vision, Workrate.
- Physical: Acceleration, Agility, Balance, Jumping, LeftFoot, NaturalFitness, Pace, RightFoot, Stamina, Strength.
- Technical: Corners, Crossing, Dribbling, Finishing, FirstTouch, Freekicks, Heading, LongShots, Longthrows, Marking, Passing, PenaltyTaking, Tackling, Technique.
- Hidden: Consistency, Dirtiness, ImportantMatches, InjuryProness, Versatility.

The JSON stores rating weights and whether an attribute is ability-dependent. It is not a raw save decoder and does not establish that these property names map one-to-one to a particular byte sequence in the Touch save.

## Engineering decision

Use this archive to guide future field labels, player-editor coverage, and rating terminology. Do not use it to guess binary offsets or unlock write controls.

The FM21 Touch save research still needs a proven join from person identity to the player-specific record, then controlled fixtures for CA, PA, and individual attributes. Every field must be read back from the exported save and verified in FM21 Touch before it can be marked writable.

## Relationship to current implementation

- Stable UID-to-person-entity mapping is implemented read-only.
- Player/staff classification remains unverified.
- CA, PA, and attribute storage locations remain unverified.
- The production FMV writer rejects edited component payloads until semantic field mappings and in-game reload validation exist.
- FMRTE parity is a product scope reference, not a claim of feature parity.
