CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS city_catalog (
  id integer PRIMARY KEY,
  relation_id bigint NOT NULL UNIQUE,
  name text NOT NULL,
  province text,
  catalog jsonb NOT NULL,
  refreshed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS product_group (
  id serial PRIMARY KEY,
  match_key text NOT NULL UNIQUE,
  name text NOT NULL,
  name_norm text NOT NULL,
  unit_kind text NOT NULL CHECK (unit_kind IN ('mass','volume','count')),
  search_tsv tsvector GENERATED ALWAYS AS (to_tsvector('spanish', name_norm)) STORED
);
CREATE INDEX IF NOT EXISTS group_text_idx ON product_group USING gin(search_tsv);
CREATE INDEX IF NOT EXISTS group_trigram_idx ON product_group USING gin(name_norm gin_trgm_ops);

CREATE TABLE IF NOT EXISTS canonical_product (
  id serial PRIMARY KEY,
  match_key text NOT NULL UNIQUE,
  group_id integer NOT NULL REFERENCES product_group,
  name text NOT NULL,
  brand text,
  ean text,
  pack_total double precision NOT NULL CHECK(pack_total > 0),
  unit_kind text NOT NULL
);
CREATE INDEX IF NOT EXISTS canonical_ean_idx ON canonical_product(ean);

CREATE TABLE IF NOT EXISTS chain_product (
  id bigserial PRIMARY KEY,
  city_id integer NOT NULL REFERENCES city_catalog,
  chain_id integer NOT NULL,
  external_id text NOT NULL,
  canonical_id integer NOT NULL REFERENCES canonical_product,
  name text NOT NULL,
  url text NOT NULL,
  last_seen_at timestamptz NOT NULL,
  UNIQUE(city_id, chain_id, external_id)
);
CREATE INDEX IF NOT EXISTS chain_canonical_idx ON chain_product(canonical_id, city_id, chain_id);

CREATE TABLE IF NOT EXISTS price (
  id bigserial PRIMARY KEY,
  chain_product_id bigint NOT NULL REFERENCES chain_product,
  canonical_id integer NOT NULL REFERENCES canonical_product,
  price numeric(12,4) NOT NULL CHECK(price > 0),
  confidence double precision NOT NULL CHECK(confidence BETWEEN 0 AND 1),
  observed_at timestamptz NOT NULL,
  source text NOT NULL CHECK(source IN ('online','open_prices','user'))
);
CREATE INDEX IF NOT EXISTS price_latest_idx ON price(chain_product_id, observed_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS alias_vote (
  query_hash text NOT NULL,
  target_key text NOT NULL,
  installation_hash text NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(query_hash, installation_hash)
);

