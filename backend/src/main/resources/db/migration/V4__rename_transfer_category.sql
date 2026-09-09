-- The reserved transfer category reads as "Internal Transfer" everywhere.
update category
set name = 'Internal Transfer'
where name = 'Transfer';
