-- // CB-1455 remove proxyconfig environment connection
-- Migration SQL that makes the change goes here.

DROP TABLE IF EXISTS public.env_proxy;
DROP INDEX IF EXISTS public.idx_env_proxy_envid;
DROP INDEX IF EXISTS public.idx_env_proxy_proxyid;

-- //@UNDO
-- SQL to undo the change goes here.

CREATE TABLE IF NOT EXISTS env_proxy (
    envid bigint NOT NULL,
    proxyid bigint NOT NULL,
    CONSTRAINT fk_env_proxy_envid FOREIGN KEY (envid) REFERENCES environment(id),
    CONSTRAINT fk_env_proxy_proxyid FOREIGN KEY (proxyid) REFERENCES proxyconfig(id),
    CONSTRAINT uk_env_proxy_envid_proxyid UNIQUE (envid, proxyid)
);
CREATE INDEX IF NOT EXISTS idx_env_proxy_envid ON env_proxy (envid);
CREATE INDEX IF NOT EXISTS idx_env_proxy_proxyid ON env_proxy (proxyid);
