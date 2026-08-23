create extension if not exists pg_trgm;

create table image_analyses (
    id uuid primary key,
    file_id uuid not null unique references file_assets(id),
    status varchar(16) not null,
    ai_description text null,
    generated_time timestamp with time zone null,
    attempt_count integer not null default 0,
    next_attempt_time timestamp with time zone null,
    constraint ck_image_analysis_status check (status in ('PENDING','PROCESSING','COMPLETED','FAILED')),
    constraint ck_image_analysis_attempt_count check (attempt_count >= 0)
);

create table image_analysis_tags (
    analysis_id uuid not null references image_analyses(id),
    position integer not null,
    tag_value varchar(80) not null,
    primary key (analysis_id, position)
);

create table search_documents (
    id uuid primary key,
    material_id uuid not null references materials(id),
    current_version_id uuid not null references material_versions(id),
    file_id uuid null references file_assets(id),
    source_type varchar(16) not null,
    content text not null,
    constraint ck_search_source_type check (source_type in ('MATERIAL','IMAGE_AI')),
    constraint ck_search_source_file check (
        (source_type = 'MATERIAL' and file_id is null)
        or (source_type = 'IMAGE_AI' and file_id is not null)
    )
);

create unique index uq_search_material_document
    on search_documents(material_id, current_version_id, source_type)
    where file_id is null;

create unique index uq_search_image_document
    on search_documents(material_id, current_version_id, file_id, source_type)
    where file_id is not null;

create index ix_image_analysis_claim
    on image_analyses(status, next_attempt_time);

create index ix_search_documents_current
    on search_documents(material_id, current_version_id);

create index ix_search_documents_content_trgm
    on search_documents using gin (content gin_trgm_ops);
