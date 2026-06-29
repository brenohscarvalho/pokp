#!/usr/bin/env node
/**
 * Generates the bundled Pokédex dataset (app/src/main/assets/pokedex.json) from PokeAPI.
 *
 * The output matches the SeedBundle Kotlin model so the app can import it directly.
 * Runs in CI (where PokeAPI is reachable) before the APK is built.
 *
 * Env vars:
 *   MAX_ID      highest national dex id to include (default 1025)
 *   CONCURRENCY parallel requests (default 16)
 *   OUT         output path (default app/src/main/assets/pokedex.json)
 */

import { writeFile, mkdir } from 'node:fs/promises';
import { dirname } from 'node:path';

const API = 'https://pokeapi.co/api/v2';
const MAX_ID = Number(process.env.MAX_ID || 1025);
const CONCURRENCY = Number(process.env.CONCURRENCY || 16);
const OUT = process.env.OUT || 'app/src/main/assets/pokedex.json';

function formatName(raw) {
  return raw
    .split('-')
    .map((p) => (p ? p[0].toUpperCase() + p.slice(1) : p))
    .join(' ');
}

const ROMAN = { i: 1, ii: 2, iii: 3, iv: 4, v: 5, vi: 6, vii: 7, viii: 8, ix: 9 };
function generationFromName(name) {
  return ROMAN[(name || '').replace('generation-', '').toLowerCase()] || 0;
}

function idFromUrl(url) {
  const parts = url.replace(/\/$/, '').split('/');
  return Number(parts[parts.length - 1]) || 0;
}

async function fetchJson(url, attempts = 4) {
  for (let i = 0; i < attempts; i++) {
    try {
      const res = await fetch(url);
      if (res.ok) return await res.json();
      if (res.status === 404) return null;
    } catch (e) {
      // retry
    }
    await new Promise((r) => setTimeout(r, 500 * (i + 1)));
  }
  throw new Error(`Failed to fetch ${url}`);
}

/** Runs async [fn] over [items] with bounded concurrency, preserving order. */
async function mapLimit(items, limit, fn) {
  const results = new Array(items.length);
  let index = 0;
  let completed = 0;
  async function worker() {
    while (index < items.length) {
      const current = index++;
      results[current] = await fn(items[current], current);
      completed++;
      if (completed % 50 === 0 || completed === items.length) {
        process.stdout.write(`  ${completed}/${items.length}\r`);
      }
    }
  }
  await Promise.all(Array.from({ length: limit }, worker));
  process.stdout.write('\n');
  return results;
}

function conditionText(details) {
  const d = (details && details[0]) || null;
  if (!d) return 'Especial';
  const parts = [];
  if (d.min_level != null) parts.push(`Nv. ${d.min_level}`);
  else if (d.item) parts.push(`Usar ${formatName(d.item.name)}`);
  else if (d.trigger && d.trigger.name === 'trade') {
    parts.push('Troca');
    if (d.held_item) parts.push(`segurando ${formatName(d.held_item.name)}`);
  } else if (d.min_happiness != null) parts.push('Amizade alta');
  else if (d.known_move) parts.push(`Conhecer ${formatName(d.known_move.name)}`);
  else if (d.location) parts.push(`Em ${formatName(d.location.name)}`);
  else if (d.held_item) parts.push(`Segurar ${formatName(d.held_item.name)}`);
  else parts.push(formatName((d.trigger && d.trigger.name) || 'especial'));
  if (d.time_of_day) parts.push(`(${d.time_of_day})`);
  return parts.join(' ');
}

function flattenChain(root) {
  const nodes = [];
  function walk(link, fromId) {
    const id = idFromUrl(link.species.url);
    nodes.push({
      id,
      name: formatName(link.species.name),
      evolvesFromId: fromId,
      condition: fromId == null ? '' : conditionText(link.evolution_details),
    });
    (link.evolves_to || []).forEach((child) => walk(child, id));
  }
  walk(root, null);
  return nodes;
}

async function main() {
  console.log(`Generating dataset (ids 1..${MAX_ID}, concurrency ${CONCURRENCY})`);

  const ids = Array.from({ length: MAX_ID }, (_, i) => i + 1);

  console.log('Fetching Pokémon + species...');
  const entries = await mapLimit(ids, CONCURRENCY, async (id) => {
    const [p, s] = await Promise.all([
      fetchJson(`${API}/pokemon/${id}`),
      fetchJson(`${API}/pokemon-species/${id}`),
    ]);
    if (!p || !s) return null;
    return { p, s };
  });

  const valid = entries.filter(Boolean);

  const pokemon = valid.map(({ p, s }) => {
    const statBy = {};
    for (const st of p.stats) statBy[st.stat.name] = st.base_stat;
    const flavorEntry =
      (s.flavor_text_entries || []).find((e) => e.language.name === 'en') || null;
    const flavorText = flavorEntry
      ? flavorEntry.flavor_text.replace(/[\n\f]/g, ' ').replace(/\s+/g, ' ').trim()
      : '';
    return {
      id: p.id,
      name: formatName(p.name),
      types: p.types.sort((a, b) => a.slot - b.slot).map((t) => t.type.name),
      generation: generationFromName(s.generation.name),
      height: p.height,
      weight: p.weight,
      baseStats: {
        hp: statBy.hp || 0,
        attack: statBy.attack || 0,
        defense: statBy.defense || 0,
        spAttack: statBy['special-attack'] || 0,
        spDefense: statBy['special-defense'] || 0,
        speed: statBy.speed || 0,
      },
      abilities: p.abilities.map((a) => formatName(a.ability.name)),
      flavorText,
      evolutionChainId: s.evolution_chain ? idFromUrl(s.evolution_chain.url) : 0,
      moves: p.moves.map((m) => {
        const detail =
          [...m.version_group_details].sort(
            (a, b) => a.level_learned_at - b.level_learned_at,
          )[0] || null;
        return {
          name: m.move.name,
          level: detail ? detail.level_learned_at : 0,
          method: detail ? detail.move_learn_method.name : 'other',
        };
      }),
    };
  });

  console.log('Fetching evolution chains...');
  const chainIds = [...new Set(pokemon.map((p) => p.evolutionChainId).filter((x) => x > 0))];
  const chains = (
    await mapLimit(chainIds, CONCURRENCY, async (cid) => {
      const c = await fetchJson(`${API}/evolution-chain/${cid}`);
      if (!c) return null;
      return { id: c.id, nodes: flattenChain(c.chain) };
    })
  ).filter(Boolean);

  console.log('Fetching move details...');
  const moveNames = [...new Set(pokemon.flatMap((p) => p.moves.map((m) => m.name)))];
  const moves = (
    await mapLimit(moveNames, CONCURRENCY, async (name) => {
      const m = await fetchJson(`${API}/move/${name}`);
      if (!m) return null;
      return {
        name: m.name,
        displayName: formatName(m.name),
        type: m.type ? m.type.name : null,
        power: m.power,
        accuracy: m.accuracy,
        pp: m.pp,
        damageClass: m.damage_class ? m.damage_class.name : null,
      };
    })
  ).filter(Boolean);

  const bundle = { version: 1, pokemon, moves, evolutionChains: chains };

  await mkdir(dirname(OUT), { recursive: true });
  await writeFile(OUT, JSON.stringify(bundle));
  console.log(
    `Wrote ${OUT}: ${pokemon.length} Pokémon, ${moves.length} moves, ${chains.length} chains`,
  );
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
