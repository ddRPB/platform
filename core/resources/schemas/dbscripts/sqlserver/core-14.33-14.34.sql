/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
ALTER PROCEDURE core.fn_dropifexists (@objname VARCHAR(250), @objschema VARCHAR(50), @objtype VARCHAR(50), @subobjname VARCHAR(250) = NULL)
AS
BEGIN
  DECLARE @ret_code INTEGER
  DECLARE @print_cmds CHAR(1)
  SELECT @print_cmds ='F'
  DECLARE @fullname VARCHAR(300)
  SELECT @ret_code = 0
  SELECT @fullname = (LOWER(@objschema) + '.' + LOWER(@objname))
  IF (UPPER(@objtype)) = 'TABLE'
    BEGIN
      IF OBJECTPROPERTY(OBJECT_ID(@fullname), 'IsTable') =1
        BEGIN
          EXEC('DROP TABLE ' + @fullname )
          SELECT @ret_code = 1
        END
      ELSE IF @objname LIKE '##%' AND OBJECT_ID('tempdb.dbo.' + @objname) IS NOT NULL
        BEGIN
          EXEC('DROP TABLE ' + @objname )
          SELECT @ret_code = 1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'VIEW'
    BEGIN
      IF OBJECTPROPERTY(OBJECT_ID(@fullname),'IsView') =1
        BEGIN
          EXEC('DROP VIEW ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'INDEX'
    BEGIN
      DECLARE @fullername VARCHAR(500)
      SELECT @fullername = @fullname + '.' + @subobjname
      IF INDEXPROPERTY(OBJECT_ID(@fullname), @subobjname, 'IndexID') IS NOT NULL
        BEGIN
          EXEC('DROP INDEX ' + @fullername )
          SELECT @ret_code =1
        END
      ELSE IF EXISTS (SELECT * FROM sys.indexes si
      WHERE si.name = @subobjname
            AND OBJECT_NAME(si.object_id) <> @objname)
        BEGIN
          RAISERROR ('Index does not belong to specified table ' , 16, 1)
          RETURN @ret_code
        END
    END
  ELSE IF (UPPER(@objtype)) = 'CONSTRAINT'
    BEGIN
      IF OBJECTPROPERTY(OBJECT_ID(LOWER(@objschema) + '.' + LOWER(@subobjname)), 'IsConstraint') = 1
        BEGIN
          EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @subobjname)
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'DEFAULT'
    BEGIN
      DECLARE @DEFAULT sysname
      SELECT 	@DEFAULT = s.name
      FROM sys.objects s
        join sys.columns c ON s.object_id = c.default_object_id
      WHERE
        s.type = 'D'
        and c.object_id = OBJECT_ID(@fullname)
        and c.name = @subobjname

      IF @DEFAULT IS NOT NULL AND OBJECTPROPERTY(OBJECT_ID(LOWER(@objschema) + '.' + LOWER(@DEFAULT)), 'IsConstraint') = 1
        BEGIN
          EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @DEFAULT)
          if (@print_cmds='T') PRINT('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @DEFAULT)
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'SCHEMA'
    BEGIN
      DECLARE @schemaid INT, @principalid int

      SELECT @schemaid=schema_id, @principalid=principal_id
      FROM sys.schemas
      WHERE name = LOWER(@objschema)

      IF @schemaid IS NOT NULL
        BEGIN
          IF (@objname is NOT NULL AND @objname NOT IN ('', '*'))
            BEGIN
              RAISERROR ('Invalid @objname for @objtype of SCHEMA   must be either "*" (to drop all dependent objects) or NULL (for dropping empty schema )' , 16, 1)
              RETURN @ret_code
            END
          ELSE IF (@objname = '*' )
            BEGIN
              DECLARE @fkConstName sysname, @fkTableName sysname, @fkSchema sysname
              DECLARE fkCursor CURSOR for
                SELECT object_name(sfk.object_id) as fk_constraint_name, object_name(sfk.parent_object_id) as fk_table_name,
                       schema_name(sfk.schema_id) as fk_schema_name
                FROM sys.foreign_keys sfk
                  INNER JOIN sys.objects fso ON (sfk.referenced_object_id = fso.object_id)
                WHERE fso.schema_id=@schemaid
                      AND sfk.type = 'F'

              OPEN fkCursor
              FETCH NEXT FROM fkCursor INTO @fkConstName, @fkTableName, @fkSchema
              WHILE @@fetch_status = 0
                BEGIN
                  SELECT @fullname = @fkSchema + '.' +@fkTableName
                  EXEC('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @fkConstName)
                  if (@print_cmds='T') PRINT('ALTER TABLE ' + @fullname + ' DROP CONSTRAINT ' + @fkConstName)

                  FETCH NEXT FROM fkCursor INTO @fkConstName, @fkTableName, @fkSchema
                END
              CLOSE fkCursor
              DEALLOCATE fkCursor

              DECLARE @soName sysname, @parent INT, @type CHAR(2), @fkschemaid int
              DECLARE soCursor CURSOR for
                SELECT so.name, so.type, so.parent_object_id, so.schema_id
                FROM sys.objects so
                WHERE (so.schema_id=@schemaid)
                ORDER BY (CASE  WHEN so.type='V' THEN 1
                          WHEN so.type='P' THEN 2
                          WHEN so.type IN ('FN', 'IF', 'TF', 'FS', 'FT') THEN 3
                          WHEN so.type='AF' THEN 4
                          WHEN so.type='U' THEN 5
                          ELSE 6
                          END)
              OPEN soCursor
              FETCH NEXT FROM soCursor INTO @soName, @type, @parent, @fkschemaid
              WHILE @@fetch_status = 0
                BEGIN
                  SELECT @fullname = @objschema + '.' + @soName
                  IF (@type = 'V')
                    BEGIN
                      EXEC('DROP VIEW ' + @fullname)
                      if (@print_cmds='T') PRINT('DROP VIEW ' + @fullname)
                    END
                  ELSE IF (@type = 'P')
                    BEGIN
                      EXEC('DROP PROCEDURE ' + @fullname)
                      if (@print_cmds='T') PRINT('DROP PROCEDURE ' + @fullname)
                    END
                  ELSE IF (@type IN ('FN', 'IF', 'TF', 'FS', 'FT'))
                    BEGIN
                      EXEC('DROP FUNCTION ' + @fullname)
                      if (@print_cmds='T') PRINT('DROP FUNCTION ' + @fullname)
                    END
                  ELSE IF (@type = 'AF')
                    BEGIN
                      EXEC('DROP AGGREGATE ' + @fullname)
                      if (@print_cmds='T') PRINT('DROP AGGREGATE ' + @fullname)
                    END
                  ELSE IF (@type = 'U')
                    BEGIN
                      EXEC('DROP TABLE ' + @fullname)
                      if (@print_cmds='T') PRINT('DROP TABLE ' + @fullname)
                    END
                  ELSE
                    BEGIN
                      DECLARE @msg NVARCHAR(255)
                      SELECT @msg=' Found object of type: ' + @type + ' name: ' + @fullname + ' in this schema.  Schema not dropped. '
                      RAISERROR (@msg, 16, 1)
                      RETURN @ret_code
                    END
                  FETCH NEXT FROM soCursor INTO @soName, @type, @parent, @fkschemaid
                END
              CLOSE soCursor
              DEALLOCATE soCursor
            END

          IF (LOWER(@objSchema) != 'dbo')
            BEGIN
              DECLARE @approlename sysname
              SELECT @approlename = name
              FROM sys.database_principals
              WHERE principal_id=@principalid AND type='A'

              IF (@approlename IS NOT NULL)
                BEGIN
                  EXEC sp_dropapprole @approlename
                  if (@print_cmds='T') PRINT ('sp_dropapprole '+ @approlename)
                END
              ELSE
                BEGIN
                  EXEC('DROP SCHEMA ' + @objschema)
                  if (@print_cmds='T') PRINT('DROP SCHEMA ' + @objschema)
                END
            END
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'PROCEDURE'
    BEGIN
      IF (@objschema = 'sys')
        BEGIN
          RAISERROR ('Invalid @objschema, not attempting to drop sys object', 16, 1)
          RETURN @ret_code
        END
      IF OBJECTPROPERTY(OBJECT_ID(@fullname),'IsProcedure') =1
        BEGIN
          EXEC('DROP PROCEDURE ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'FUNCTION'
    BEGIN
      IF EXISTS (SELECT 1 FROM sys.objects o JOIN sys.schemas s ON o.schema_id = s.schema_id WHERE s.name = @objschema AND o.name = @objname AND o.type IN ('FN', 'IF', 'TF', 'FS', 'FT'))
        BEGIN
          EXEC('DROP FUNCTION ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE IF (UPPER(@objtype)) = 'AGGREGATE'
    BEGIN
      IF EXISTS (SELECT 1 FROM sys.objects o JOIN sys.schemas s ON o.schema_id = s.schema_id WHERE s.name = @objschema AND o.name = @objname AND o.type IN ('AF'))
        BEGIN
          EXEC('DROP AGGREGATE ' + @fullname )
          SELECT @ret_code =1
        END
    END
  ELSE
    RAISERROR('Invalid object type - %s   Valid values are TABLE, VIEW, INDEX, CONSTRAINT, DEFAULT, SCHEMA, PROCEDURE, FUNCTION, AGGREGATE ', 16,1, @objtype )

  RETURN @ret_code;
END

GO