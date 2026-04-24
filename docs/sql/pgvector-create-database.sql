-- Run this script on an admin connection, for example connected to the
-- default "postgres" database as a user with CREATE DATABASE privilege.

CREATE DATABASE smart_ticket_vector
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    TEMPLATE = template0;
