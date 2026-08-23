alter table material_versions
    add constraint uq_material_version_identity unique (material_id, id);

alter table materials
    add constraint fk_materials_current_version_owner
    foreign key (id, current_version_id)
    references material_versions (material_id, id)
    deferrable initially deferred;

comment on constraint fk_materials_current_version_owner on materials is
    'Ensures the current version belongs to the material; deferred so material and v1 can be inserted in one transaction';
