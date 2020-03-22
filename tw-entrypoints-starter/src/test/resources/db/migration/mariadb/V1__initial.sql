CREATE TABLE table_a
(
    id      bigint PRIMARY KEY,
    version SMALLINT NOT NULL
);

CREATE TABLE table_b
(
    id      bigint PRIMARY KEY,
    version SMALLINT NOT NULL,
    a_id    bigint   NOT NULL
);
