-- Seed Users
-- Password BCrypt hash of 'admin123': '$2a$10$EpJpXyK1l59a72w3eE0e5jG.374n2v9RGe8p9nO2wGz7u9K2bN1XG'
-- Password BCrypt hash of 'user123': '$2a$10$r8Ua2/J3hY1w9rM4sV5uCe9T0jK.5y1o1R5L5v/5W6P2pC4K8d3mS'

INSERT INTO users (username, email, password_hash, role) VALUES 
('admin', 'admin@security.com', '$2a$10$EpJpXyK1l59a72w3eE0e5jG.374n2v9RGe8p9nO2wGz7u9K2bN1XG', 'ADMIN'),
('user1', 'user1@security.com', '$2a$10$r8Ua2/J3hY1w9rM4sV5uCe9T0jK.5y1o1R5L5v/5W6P2pC4K8d3mS', 'USER'),
('user2', 'user2@security.com', '$2a$10$r8Ua2/J3hY1w9rM4sV5uCe9T0jK.5y1o1R5L5v/5W6P2pC4K8d3mS', 'USER');

-- Seed OAuth2 Client
INSERT INTO oauth2_clients (client_id, client_secret, client_name, redirect_uri, allowed_scopes) VALUES
('legit_app', '$2a$10$r8Ua2/J3hY1w9rM4sV5uCe9T0jK.5y1o1R5L5v/5W6P2pC4K8d3mS', 'Legitimate App', 'http://localhost:8080/oauth/callback', 'read,write');
