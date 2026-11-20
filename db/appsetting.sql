-- ---------- app_setting ----------
INSERT INTO `app_setting`
(`id`, `name`, `setting_value`, `secret`, `description`, `enabled`,
 `created_at`, `updated_at`, `is_deleted`)
VALUES
    (1, 'nim.model', 'nvidia/nemotron-3-super-120b-a12b', 0,
     'NIM model used for text-to-SQL', 1,
     NOW(6), NOW(6), 0),

    (2, 'nim.base-url', 'https://integrate.api.nvidia.com/v1', 0,
     'NVIDIA NIM OpenAI-compatible base URL', 1,
     NOW(6), NOW(6), 0),

    (3, 'nim.api-key', 'paste api key here', 1,
     'NVIDIA NIM API key', 1,
     NOW(6), NOW(6), 0),

    (4, 'ai.show-sql', 'true', 0,
     'Show the AI-generated SQL in the Ask AI tab. Turn off to show only the data it returns.',
     1, NOW(6), NOW(6), 0);

SET FOREIGN_KEY_CHECKS = 1;