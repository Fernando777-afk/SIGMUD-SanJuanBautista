-- Permite alta y edición del catálogo de sectores desde el módulo Mercados.
GRANT INSERT (codigo, nombre), UPDATE (nombre)
ON sigmud_db.sectores_mercado TO 'sigmud_app'@'127.0.0.1';
