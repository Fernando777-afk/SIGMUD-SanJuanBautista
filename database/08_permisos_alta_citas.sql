-- Autoriza fijar el estado inicial y la fecha del servidor al reservar una cita.
GRANT INSERT (estado, registrado_en)
ON sigmud_db.citas TO 'sigmud_app'@'127.0.0.1';
