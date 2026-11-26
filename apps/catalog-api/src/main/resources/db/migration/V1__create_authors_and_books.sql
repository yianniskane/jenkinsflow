CREATE TABLE authors (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name NVARCHAR(255) NOT NULL
);

CREATE TABLE books (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    title NVARCHAR(255) NOT NULL,
    author_id BIGINT NOT NULL,
    published_year INT NOT NULL,
    CONSTRAINT fk_books_author FOREIGN KEY (author_id) REFERENCES authors (id)
);

CREATE INDEX ix_books_author_id ON books (author_id);
