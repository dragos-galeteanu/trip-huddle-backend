# amqp

The messaging library shared by every service that talks over RabbitMQ. It holds the broker configuration, the publisher, and the definition of every message: one **event** record and one **contract** class per message.

Services never call each other over HTTP. Everything between them goes through the queues defined here.

## In short

The shared language services speak over RabbitMQ. It defines every message, which queues and exchanges carry it, and how failures are retried, so a publisher and its consumers always agree.

**Stack:** Spring AMQP with RabbitMQ, Jackson JSON messages.

## User stories

- As a service, I publish an event once, and every service that cares receives it on its own queue.
- As a service, I can take the same message twice without harm, and a message I keep failing on ends up in a dead-letter queue instead of blocking the queue.
- As a developer, I add a consumer to an existing event by adding a queue and a binding here, without touching the publisher.
- As a developer, I find every message, its producer and its consumers in one catalogue.

## Requirements

- **MQ-1** Each message is an event record plus a contract class naming its queue, dead-letter queue and, for several consumers, its fanout exchange.
- **MQ-2** One consumer: the message goes to the default exchange with the queue as routing key. Several consumers: one fanout exchange, with a queue per consuming service.
- **MQ-3** Durable queues and exchanges are declared at startup. A binding to a queue no contract lists fails startup.
- **MQ-4** A failing listener is retried three times with growing pauses, then the message goes to a dead-letter queue.
- **MQ-5** Values both ends must agree on, such as `LicenseTarget` and `BillingInterval`, live here, not in each service.

---

## Technical reference

The sections below describe how it works.

## Conventions

- **Event**: a Java record with the data, for example `GroupInvitationAcceptedEvent(groupInvitationId, groupId, email, role)`.
- **Contract**: a class with two constants, the queue name and its dead-letter queue name.
- **Queue names**: `<domain>.<thing>.<action>.queue` and `.dlq`, for example `group.invitation.accepted.queue`.
- **Registration**: each domain has a `*Queues` class listing its queue names. `RabbitMQConfig` declares all of them as durable queues at startup, in whichever service starts first.
- **Publishing**:
  - **One consumer:** `RabbitMQMessageProducer.publish(event, "", queueName)`. The empty exchange is RabbitMQ's default exchange, so the message goes straight to the named queue.
  - **Several consumers:** `publish(event, XxxContract.XXX_EXCHANGE_NAME, "")`.
- **One consumer per queue.** A queue is a work queue, not a topic: if two services listened on the same queue, each message would reach only one of them.
- **Fanout exchanges for several consumers.** An event that several services consume has a durable fanout exchange, `<domain>.<thing>.<action>.exchange`, and one queue per consuming service bound to it.
  - Example: `group.membership.created.exchange` feeds `group.membership.created.queue` (`api-chat`), `.license.queue` (`api-license`) and `.payment.queue` (`api-payment`), all named in `GroupMembershipCreatedContract`.
  - The publisher sends once. `RabbitMQConfig` declares the exchanges and bindings listed in each domain's `*Exchanges` class: `GroupExchanges`, `UserExchanges` and `LicenseExchanges`.
  - It refuses to start if a binding names a queue no `*Queues` class lists.
  - **Exchanges today:** the four group-membership events, group disabled and deleted, user disabled and deleted, and license subscription activated, canceled and ended. Group events and license activated and ended also feed `api-destination`'s `.destination` queues. `destination.itinerary.finished.exchange` (`DestinationExchanges`) feeds `api-chat` and `api-user`, and user deleted feeds `api-destination` too.
- **Adding a consumer** to such an event: add a `XXX_<SERVICE>_QUEUE_NAME` and its DLQ to the contract, list them in the `*Queues` class, bind the queue in the `*Exchanges` class, and listen on it. The publisher doesn't change.
- **Making a single-consumer event fan out:** add the exchange name to the contract, add it to the `*Exchanges` class with the existing queue, and change its publisher to the exchange. The existing consumer keeps its queue.
- **Format**: JSON, through `Jackson2JsonMessageConverter`.
- **Shared types**: a value both ends of a message must agree on lives next to the messages, so services don't each keep a copy. Both travel as their names, so the JSON is unchanged:
  - `license.BillingInterval` (`MONTH`, `YEAR`, with `months()` and `addTo` for UTC calendar periods), typed in `LicenseCatalogChangedEvent` and `LicenseSubscriptionPendingEvent`;
  - `license.LicenseTarget` (`USER`, `GROUP`): who a license is for, and the type of a subscriber. It is typed as `target` and `subscriberType` in every license event.
  - `api-license`, `api-payment` and, for the license emails, `api-user` use them.

## Delivery and failure

```mermaid
flowchart LR
    P[Publisher service] -->|"publish(event, '', queue)"| Q[(queue)]
    Q --> L[Listener]
    L -->|success| Done([done])
    L -->|exception| R{fewer than 3 attempts?}
    R -->|"yes: wait 2s, 4s"| L
    R -->|no| D[(default.dlq)]
```

Listeners that use `simpleRabbitListenerContainerFactory` get this retry policy. The notification service has its own factories that dead-letter to `notification.email.dlq` and `notification.push.dlq` instead.

## Message catalogue

| Domain | Messages | Published by | Consumed by |
|---|---|---|---|
| `notification` | `SendEmailRequest` | `api-user`, `api-group` | `notification` |
| `notification` | `SendPushRequest` | `api-chat` | `notification` |
| `user` | `UserRegisteredEvent`, `UserDisabledEvent`, `UserDeletedEvent` | `api-user` | `api-chat`; Disabled and Deleted go through fanout exchanges, to `api-payment` too, and Deleted to `api-destination` |
| `user` | `UserContactUpdatedEvent`: email, name and whether the email is verified, on registering, verifying and changing them | `api-user` | `api-payment` |
| `user` | `UserDeviceRegisteredEvent`, `UserDeviceUnregisteredEvent` | `api-user` | `notification` |
| `user` | `UserTravelDocumentsUpdatedEvent`: a user's nationalities and residence permits (`ResidencePermitKind`), the whole set with `changedAt` | `api-user` | `api-destination` |
| `user` | `UserProfilePictureUpdatedEvent` | `api-user` | nobody yet |
| `user` | `UserEmailVerifiedEvent` | `api-user` | `api-group` |
| `user` | `UserMatchedToGroupInvitationEvent` | `api-user` | `api-group` |
| `platform` | Country × Created, Disabled, Deleted (3) | `api-platform` | `api-destination` |
| `platform` | Currency, Language × Created, Disabled, Deleted (6) | `api-platform` | nobody yet |
| `group` | Group × Created, Updated, Disabled, Deleted (4) | `api-group` | `api-chat`; Disabled and Deleted through fanout exchanges to `api-license`, `api-payment` and `api-destination` too |
| `group` | GroupMembership × Created, Updated, Disabled, Deleted (4) | `api-group` | `api-chat`, `api-license`, `api-payment` and `api-destination`, through fanout exchanges |
| `group` | GroupInvitation × Created, Updated, Disabled, Deleted, Rejected, Canceled (6) | `api-group` | nobody yet |
| `group` | `GroupInvitationAcceptedEvent` | `api-group` | `api-user` |
| `chat` | ChatMessage × Sent, Edited, Deleted, Disabled, Reported (5) | `api-chat` | nobody yet |
| `chat` | SupportTicket × Created, StatusChanged, Replied (3) | `api-chat` | `api-user` |
| `license` | LicenseSubscription × Activated, Canceled, Ended, RenewalDue (4) | `api-license` | `api-user`; Activated, Canceled and Ended go through fanout exchanges, to `api-payment` too, and Activated and Ended to `api-destination` |
| `license` | `LicenseSubscriptionPendingEvent`: what a subscription waiting for payment must be charged | `api-license` | `api-payment` |
| `license` | `LicenseCatalogChangedEvent`: a published or archived license's whole state | `api-license` | `api-payment` |
| `license` | `LicenseSubscriptionActivationRefusedEvent`: a payment for a subscription that no longer waits for one | `api-license` | `api-payment` |
| `payment` | `PaymentSubscriptionActivatedEvent`: a subscription's first payment went through at checkout or by a switch, with Stripe's period | `api-payment` | `api-license` |
| `payment` | PaymentSubscription × Renewed, PastDue, Ended, RenewalChanged (4): what Stripe did to a subscription it bills | `api-payment` | `api-license` |
| `license` | `LicenseSubscriptionResumedEvent`, and `LicenseSubscriptionCanceledEvent` on a `.payment` queue | `api-license` | `api-payment` |
| `notification` | `SendEmailRequest` for payment emails | `api-payment` | `notification` |
| `ai` | `AiTextRequestedEvent`, `AiEmbeddingRequestedEvent`: AI jobs, each naming its reply queue | any service | `openai` |
| `ai` | `AiTextCompletedEvent`, `AiEmbeddingCompletedEvent`: the answers, on the requester's queue in `AiQueues.REPLY_QUEUE_NAMES` | `openai` | the requester: `api-destination` on `ai.text.completed.destination.queue` and `ai.embedding.completed.destination.queue` |
| `license` | `LicenseSubscriptionRenewedEvent`, `LicenseEntitlementsChangedEvent` | `api-license` | `api-destination` |
| `destination` | `ItineraryFinishedEvent`: an itinerary READY or FAILED, with its travellers and plan link, through a fanout exchange | `api-destination` | `api-chat` (sockets), `api-user` (email) |
| `notification` | `SendPushRequest` for finished itineraries, `SendEmailRequest` `ITINERARY_READY` | `api-destination`, `api-user` | `notification` |

Messages on a queue with no consumer stay there until one is added.

```mermaid
flowchart LR
    U[api-user] -- SendEmailRequest --> N[notification]
    G[api-group] -- SendEmailRequest --> N
    G -- GroupInvitationAccepted --> U
    U -- UserMatchedToGroupInvitation --> G
    U -- UserEmailVerified --> G
    U -- "UserRegistered, Disabled, Deleted" --> CH[api-chat]
    G -- "Group*, GroupMembership*" --> CH
    CH -- SendPushRequest --> N
    CH -- "SupportTicket*" --> U
    G -- "GroupMembership*, GroupDisabled, GroupDeleted" --> L[api-license]
    L -- "LicenseSubscription*" --> U
    L -- "LicenseCatalogChanged, LicenseSubscriptionPending, Activated, Ended" --> PA[api-payment]
    U -- "UserContactUpdated, UserDisabled, UserDeleted" --> PA
    G -- "GroupMembership*, GroupDisabled, GroupDeleted" --> PA
    PA -- "PaymentSubscription*" --> L
    L -- "ActivationRefused, Canceled, Resumed" --> PA
    PA -- SendEmailRequest --> N
    U -- "UserDevice*" --> N
    PL[api-platform] -- reference data events --> X([no consumer yet])
```

## Adding a message

1. Add `XxxEvent` (record) and `XxxContract` (queue and DLQ names) in the domain's package.
2. Add both names to the domain's `*Queues` class.
3. Publish with `RabbitMQMessageProducer`; consume with `@RabbitListener(queues = XxxContract.XXX_QUEUE_NAME, containerFactory = "simpleRabbitListenerContainerFactory")`.
