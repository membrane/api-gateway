CREATE TABLE users (
                        nickname  VARCHAR(255) PRIMARY KEY,
                        password  VARCHAR(255) NOT NULL
);

INSERT INTO users (nickname, password)
VALUES ('johnsmith123', 'pass123'),
       ('membrane', 'gateway');
