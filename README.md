# UNIQA CRM Email Categorization - POC

Ingests customer emails, runs them through local NLP (Apache OpenNLP) and a
user-editable Drools rule engine, and assigns each email a category
(Complaints, Claims, PolicyChanges, GeneralInquiry, or whatever categories
you add).

## Why it's built this way

- **`EmailSource` interface** (`email/EmailSource.java`) — today it's backed
  by an embedded GreenMail IMAP server seeded with sample emails, so the
  whole demo runs with zero access to real UNIQA mailboxes. When mailbox
  access is granted, implement `EmailSource` against Microsoft Graph API;
  nothing else in the app changes.
- **NLP layer** (`nlp/NlpCategorizationService.java`) — trains a real
  OpenNLP document categorizer at startup from
  `src/main/resources/nlp/training-data.txt`, entirely local, no external
  API calls. Also flags negative sentiment via a keyword heuristic.
- **Rule engine** (`rules/RuleEngineService.java`) — the rule-builder UI
  will call `/api/rules` to create rows like *"BODY contains 'claim' AND
  sentiment negative -> Complaints"*. Those rows get compiled into Drools
  DRL at runtime, so business users never write DRL by hand, but Drools
  still does the actual evaluation. A rule match wins; if nothing matches,
  the raw NLP guess is used as the category.

## Prerequisites

- Java 21, Maven

## Run it

```fish
mvn spring-boot:run
```

On startup it will:
1. Start the embedded test mailbox and seed 6 sample emails
2. Train the OpenNLP categorizer
3. Seed default categories + two starter rules into Postgres
4. Poll the test mailbox every 15s (see `email.poll.fixed-delay-ms`) and
   categorize anything new

## Try it

```fish
# Force an immediate fetch+categorize cycle instead of waiting for the poll
curl -X POST http://localhost:8081/api/emails/fetch-now

# See everything categorized so far
curl http://localhost:8081/api/emails

# See/manage rules (this is what the rule-builder UI will call)
curl http://localhost:8081/api/rules

# Add a new rule, e.g. route anything mentioning "cancel" to Complaints
curl -X POST http://localhost:8081/api/rules \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Cancellation mention",
    "field": "BODY",
    "operator": "CONTAINS",
    "value": "cancel",
    "requireNegativeSentiment": false,
    "targetCategoryName": "Complaints",
    "priority": 80,
    "active": true
  }'
```

## Not yet built (next steps)

- React rule-builder frontend calling `/api/rules` and `/api/categories`
- RabbitMQ wiring (currently synchronous — fine at POC volume, add a queue
  before any real load)
- Microsoft Graph API `EmailSource` implementation for real UNIQA mailboxes
- Auth on the REST API
