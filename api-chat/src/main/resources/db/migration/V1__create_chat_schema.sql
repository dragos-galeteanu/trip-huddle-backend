-- Tables of api-chat's JPA entities, as Hibernate maps them, so ddl-auto: validate passes on a new database.
-- A database whose tables predate the migrations is baselined at version 1, so this script only runs on an empty one.


create table chat_users (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    user_id uuid not null,
    primary key (id),
    constraint uk_chat_user unique (user_id)
);

create table conversation_participants (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    is_muted boolean not null,
    joined_at timestamp(6) with time zone not null,
    last_activity_at timestamp(6) with time zone not null,
    last_read_at timestamp(6) with time zone,
    last_read_message_id varchar(24),
    left_at timestamp(6) with time zone,
    muted_until timestamp(6) with time zone,
    role varchar(16) not null check (role in ('ADMIN','MEMBER','STAFF')),
    unread_count integer not null,
    user_id uuid not null,
    conversation_id uuid not null,
    primary key (id),
    constraint uk_conversation_participant unique (conversation_id, user_id)
);

create table conversations (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    group_id uuid,
    last_message_at timestamp(6) with time zone,
    last_message_id varchar(24),
    last_message_preview varchar(200),
    last_message_sender_id uuid,
    name varchar(255),
    private_key varchar(73),
    read_only_at timestamp(6) with time zone,
    type varchar(16) not null check (type in ('PRIVATE','GROUP','SUPPORT')),
    primary key (id),
    constraint uk_conversation_group unique (group_id),
    constraint uk_conversation_private_key unique (private_key)
);

create table message_reports (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    conversation_id uuid not null,
    message_id varchar(24) not null,
    reason varchar(1000) not null,
    reporter_id uuid not null,
    resolution_note varchar(1000),
    resolved_at timestamp(6) with time zone,
    resolved_by_user_id uuid,
    status varchar(16) not null check (status in ('OPEN','DISMISSED','ACTION_TAKEN')),
    primary key (id)
);

create table support_ticket_events (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    actor_id uuid,
    from_value varchar(32),
    target_user_id uuid,
    to_value varchar(32),
    type varchar(32) not null check (type in ('CREATED','STATUS_CHANGED','PRIORITY_CHANGED','CATEGORY_CHANGED','ASSIGNED','UNASSIGNED','STAFF_JOINED','STAFF_LEFT','RATED')),
    ticket_id uuid not null,
    primary key (id)
);

create table support_tickets (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    assignee_id uuid,
    category varchar(16) not null check (category in ('ACCOUNT','GROUP','PAYMENT','SAFETY','BUG','OTHER')),
    closed_at timestamp(6) with time zone,
    closed_by_user_id uuid,
    first_response_at timestamp(6) with time zone,
    group_id uuid,
    linked_conversation_id uuid,
    message_id varchar(24),
    message_report_id uuid,
    number bigint not null,
    opened_by_user_id uuid not null,
    priority varchar(16) not null check (priority in ('LOW','NORMAL','HIGH','URGENT')),
    rated_at timestamp(6) with time zone,
    rating integer,
    rating_comment varchar(1000),
    requester_id uuid not null,
    resolved_at timestamp(6) with time zone,
    status varchar(32) not null check (status in ('OPEN','IN_PROGRESS','WAITING_ON_CUSTOMER','RESOLVED','CLOSED')),
    subject varchar(200) not null,
    conversation_id uuid not null unique,
    primary key (id),
    constraint uk_support_ticket_number unique (number)
);

create table user_blocks (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    blocked_id uuid not null,
    blocker_id uuid not null,
    primary key (id),
    constraint uk_user_block unique (blocker_id, blocked_id)
);

create index ix_participant_user_activity
   on conversation_participants (user_id, last_activity_at);

create index ix_message_report_status
   on message_reports (status);

create index ix_support_ticket_event_ticket
   on support_ticket_events (ticket_id);

create index ix_support_ticket_status
   on support_tickets (status);

create index ix_support_ticket_requester
   on support_tickets (requester_id, status);

create index ix_support_ticket_assignee
   on support_tickets (assignee_id, status);

alter table if exists conversation_participants
   add constraint fk_participant_conversation
   foreign key (conversation_id)
   references conversations;

alter table if exists support_ticket_events
   add constraint fk_support_ticket_event_ticket
   foreign key (ticket_id)
   references support_tickets;

alter table if exists support_tickets
   add constraint fk_support_ticket_conversation
   foreign key (conversation_id)
   references conversations;

-- Not declared on the entities.

-- Support ticket numbers, shown to people as TH-<number>.
create sequence support_ticket_number_seq start with 1 increment by 1;

-- A user has at most one open report of a message; JPA can't declare a partial index.
create unique index uk_message_report_open
    on message_reports (message_id, reporter_id) where status = 'OPEN';
