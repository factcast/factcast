--liquibase formatted sql
--changeset usr:issue45 
update fact 
   set header= jsonb_set( 
      header, 
      '{meta}' , 
      COALESCE(header->'meta','{}') || concat('{"_ser":', ser,'}' )::jsonb 
      , true
      );