-- Tables of api-user's JPA entities, as Hibernate maps them, so ddl-auto: validate passes on a new database.
-- A database whose tables predate the migrations is baselined at version 1, so this script only runs on an empty one.


create table user_profiles (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    bio TEXT,
    country_code varchar(2) not null,
    cover_picture_url varchar(255),
    facebook_url varchar(255),
    instagram_url varchar(255),
    profile_picture_url varchar(255),
    website_url varchar(255),
    x_url varchar(255),
    user_id uuid unique,
    primary key (id)
);

create table user_travel_documents (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    country_code varchar(2) not null,
    permit_kind varchar(16) check (permit_kind in ('PERMANENT','TEMPORARY')),
    type varchar(24) not null check (type in ('NATIONALITY','RESIDENCE_PERMIT')),
    user_id uuid not null,
    valid_until date,
    primary key (id),
    constraint uk_user_travel_document_user_type_country_code unique (user_id, type, country_code)
);

create table users (
    id uuid not null,
    created_at timestamp(6) with time zone not null,
    deleted_at timestamp(6) with time zone,
    disabled_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    email varchar(255) not null,
    email_verification_token uuid,
    email_verified boolean not null,
    firebase_synced_role varchar(255) check (firebase_synced_role in ('Customer','Sales','Inspector','Support','Admin')),
    firebase_uid varchar(255) not null,
    first_name varchar(255) not null,
    last_name varchar(255) not null,
    phone_number varchar(255) not null,
    role varchar(255) not null check (role in ('Customer','Sales','Inspector','Support','Admin')),
    primary key (id),
    constraint uk_user_firebase_uid unique (firebase_uid),
    constraint uk_user_email unique (email),
    constraint uk_user_phone_number unique (phone_number)
);

alter table if exists user_profiles
   add constraint fk_user_profile_user
   foreign key (user_id)
   references users;
