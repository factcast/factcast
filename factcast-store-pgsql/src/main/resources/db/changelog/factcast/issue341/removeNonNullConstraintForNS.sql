--liquibase formatted sql
--changeset usr:issue341

ALTER TABLE tokenstore alter column ns DROP NOT NULL;

 
