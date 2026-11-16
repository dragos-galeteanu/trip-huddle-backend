-- Tables of api-group's JPA entities, as Hibernate maps them, so ddl-auto: validate passes on a new database.
-- A database whose tables predate the migrations is baselined at version 1, so this script only runs on an empty one.


create table group_invitations (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    accepted_by_user_id uuid,
    email varchar(255) not null,
    expires_at timestamp(6) with time zone not null,
    invited_by_user_id uuid not null,
    role varchar(16) not null check (role in ('ADMIN','MEMBER')),
    status varchar(16) not null check (status in ('PENDING','ACCEPTED','REJECTED','CANCELED')),
    token uuid not null,
    group_id uuid not null,
    primary key (id),
    constraint uk_group_invitation_token unique (token)
);

create table group_memberships (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    role varchar(16) not null check (role in ('ADMIN','MEMBER')),
    user_id uuid not null,
    group_id uuid not null,
    primary key (id)
);

create table groups (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    created_by_user_id uuid not null,
    description TEXT,
    name varchar(255) not null,
    primary key (id)
);

alter table if exists group_invitations
   add constraint fk_group_invitation_group
   foreign key (group_id)
   references groups;

alter table if exists group_memberships
   add constraint fk_group_membership_group
   foreign key (group_id)
   references groups;

-- A user has one membership per group and an email one pending invitation per group, among rows not deleted;
-- JPA can't declare a partial index.
create unique index uk_group_membership_group_user
    on group_memberships (group_id, user_id) where deleted_at is null;
create unique index uk_group_invitation_pending_email
    on group_invitations (group_id, email) where status = 'PENDING' and deleted_at is null;
