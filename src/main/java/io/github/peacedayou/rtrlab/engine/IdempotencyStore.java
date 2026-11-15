package io.github.peacedayou.rtrlab.engine;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mémorise le résultat de chaque transaction, indexé par sa clé (EndToEndId, ou UETR s'il est présent).
 * Un renvoi identique retrouve le même résultat sans nouvelle écriture comptable.
 */
public interface IdempotencyStore {

    record Outcome(String fingerprint, String status, String reasonCode) {}

    Optional<Outcome> find(String key);

    /** Réserve la clé (état PDNG) si elle est libre ; renvoie false si elle existait déjà. */
    boolean putIfAbsent(String key, Outcome outcome);

    /** Enregistre l'état final d'une clé réservée. */
    void complete(String key, Outcome outcome);

    class InMemory implements IdempotencyStore {
        private final Map<String, Outcome> map = new ConcurrentHashMap<>();

        @Override
        public Optional<Outcome> find(String key) {
            return Optional.ofNullable(map.get(key));
        }

        @Override
        public boolean putIfAbsent(String key, Outcome outcome) {
            return map.putIfAbsent(key, outcome) == null;
        }

        @Override
        public void complete(String key, Outcome outcome) {
            map.put(key, outcome);
        }
    }
}
