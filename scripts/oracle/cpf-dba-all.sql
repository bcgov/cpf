set serveroutput on
DECLARE
  C INTEGER;
BEGIN
	SELECT COUNT(*) INTO C FROM DBA_TABLESPACES WHERE TABLESPACE_NAME = 'CPF';
	IF C = 0 THEN
	  dbms_output.put_line('INFO: Created tablespace CPF');
    EXECUTE IMMEDIATE 'CREATE TABLESPACE CPF DATAFILE ''TABLESPACE_DIR/CPF_01.dbf'' SIZE 1G EXTENT MANAGEMENT LOCAL SEGMENT SPACE MANAGEMENT AUTO';
  ELSE
    dbms_output.put_line('INFO: Tablespace CPF exists so no need to create again');
  END IF;

  SELECT COUNT(*) INTO C FROM DBA_TABLESPACES WHERE TABLESPACE_NAME = 'CPF_NDX';
  IF C = 0 THEN
    dbms_output.put_line('INFO: Created tablespace CPF_NDX');
    EXECUTE IMMEDIATE 'CREATE TABLESPACE CPF_NDX DATAFILE ''TABLESPACE_DIR/CPF_NDX_01.dbf'' SIZE 500M EXTENT MANAGEMENT LOCAL SEGMENT SPACE MANAGEMENT AUTO';
  ELSE
    dbms_output.put_line('INFO: Tablespace CPF_NDX exists so no need to create again');
  END IF;

  SELECT COUNT(*) INTO C FROM DBA_ROLES WHERE ROLE = 'CPF_VIEWER';
  IF C = 0 THEN
    dbms_output.put_line('INFO: Created role CPF_VIEWER');
    EXECUTE IMMEDIATE 'CREATE ROLE CPF_VIEWER NOT IDENTIFIED';
  ELSE
    dbms_output.put_line('INFO: Role CPF_VIEWER exists so no need to create again');
  END IF;

  SELECT COUNT(*) INTO C FROM DBA_ROLES WHERE ROLE = 'CPF_USER';
  IF C = 0 THEN
    dbms_output.put_line('INFO: Created role CPF_USER');
    EXECUTE IMMEDIATE 'CREATE ROLE CPF_USER NOT IDENTIFIED';
  ELSE
    dbms_output.put_line('INFO: Role CPF_USER exists so no need to create again');
  END IF;
 
  SELECT COUNT(*) INTO C FROM DBA_ROLES WHERE ROLE = 'CPF_WEB_PROXY';
  IF C = 0 THEN
    dbms_output.put_line('INFO: Created role CPF_WEB_PROXY');
    EXECUTE IMMEDIATE 'CREATE ROLE CPF_WEB_PROXY NOT IDENTIFIED';
  ELSE
    dbms_output.put_line('INFO: Role CPF_WEB_PROXY exists so no need to create again');
  END IF;
  
  SELECT COUNT(*) INTO C FROM DBA_USERS WHERE USERNAME = 'CPF';
  IF C = 0 THEN
    dbms_output.put_line('INFO: Created USER CPF');
    EXECUTE IMMEDIATE 'CREATE USER CPF IDENTIFIED BY CPF_PASSWORD DEFAULT TABLESPACE CPF QUOTA UNLIMITED ON CPF QUOTA UNLIMITED ON CPF_NDX';
  ELSE
    dbms_output.put_line('INFO: User CPF exists so no need to create again');
  END IF;
  EXECUTE IMMEDIATE 'GRANT CREATE SESSION TO CPF';
  EXECUTE IMMEDIATE 'GRANT CREATE SEQUENCE TO CPF'; 
  EXECUTE IMMEDIATE 'GRANT CREATE VIEW TO CPF'; 
  EXECUTE IMMEDIATE 'GRANT CREATE ROLE TO CPF';
  EXECUTE IMMEDIATE 'GRANT CREATE TABLE TO CPF';

  SELECT COUNT(*) INTO C FROM DBA_USERS WHERE USERNAME = 'PROXY_CPF_WEB';
  IF C = 0 THEN
    dbms_output.put_line('INFO: Created USER PROXY_CPF_WEB');
    EXECUTE IMMEDIATE 'CREATE USER PROXY_CPF_WEB IDENTIFIED BY PROXY_CPF_WEB_PASSWORD DEFAULT TABLESPACE CPF QUOTA UNLIMITED ON CPF QUOTA UNLIMITED ON CPF_NDX';
  ELSE
    dbms_output.put_line('INFO: User PROXY_CPF_WEB exists so no need to create again');
  END IF;
  EXECUTE IMMEDIATE 'GRANT CREATE SESSION TO PROXY_CPF_WEB';
  EXECUTE IMMEDIATE 'GRANT CPF_USER TO PROXY_CPF_WEB';
  EXECUTE IMMEDIATE 'GRANT CPF_WEB_PROXY TO PROXY_CPF_WEB';
END;
/
exit
