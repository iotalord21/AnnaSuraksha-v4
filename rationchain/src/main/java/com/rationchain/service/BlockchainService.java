package com.rationchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.rationchain.model.Beneficiary;
import com.rationchain.model.BeneficiaryRepository;


import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Simulates a blockchain ledger in Java.
 * Uses SHA-256 chained hashes — same algorithm as a real Ethereum/Polygon chain.
 *
 * Production: replace persistence with web3j calls to deployed RationLedger.sol
 * on Polygon Amoy testnet or Hyperledger Besu (India Stack).
 */

@Service

public class BlockchainService {
    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);


    private final BeneficiaryRepository repo;

    /** SHA-256 Aadhaar hash — same as keccak256 in Solidity for demo purposes */
    public String hashAadhaar(String aadhaarRaw) {
        return sha256(aadhaarRaw.replaceAll("[^0-9]", ""));
    }

    public String hashVoterId(String voterId) {
        return sha256(voterId.trim().toUpperCase());
    }

    /**
     * Compute block hash = SHA-256(prevHash + aadhaarHash + name + state + category)
     * Mimics proof-of-work lite — deterministic for demo, nonce-based in prod.
     */
    public String computeBlockHash(String prevHash, String aadhaarHash,
                                   String name, String state, String category) {
        String payload = prevHash + aadhaarHash + name + state + category;
        return sha256(payload);
    }

    /** Returns the hash of the latest block on the chain */
    public String getLatestHash() {
        List<Beneficiary> all = repo.findAllByOrderByBlockHeightAsc();
        if (all.isEmpty()) return "0000000000000000000000000000000000000000000000000000000000000000";
        return all.get(all.size() - 1).getBlockHash();
    }

    /** Returns next block height */
    public long getNextBlockHeight() {
        List<Beneficiary> all = repo.findAllByOrderByBlockHeightAsc();
        return all.isEmpty() ? 1L : all.get(all.size() - 1).getBlockHeight() + 1L;
    }

    /**
     * Validate chain integrity — every prevBlockHash must match the
     * actual hash of the preceding block. Detects any tampering.
     */
    public boolean validateChain() {
        List<Beneficiary> chain = repo.findAllByOrderByBlockHeightAsc();
        for (int i = 1; i < chain.size(); i++) {
            String expected = chain.get(i - 1).getBlockHash();
            String actual   = chain.get(i).getPrevBlockHash();
            if (!expected.equals(actual)) {
                log.error("Chain broken at block {} — expected prev {} got {}",
                          chain.get(i).getBlockHeight(), expected, actual);
                return false;
            }
        }
        return true;
    }

    /** Format a full hash as "0x1a2b...ef34" for display */
    public String shortHash(String hash) {
        if (hash == null || hash.length() < 12) return hash;
        return "0x" + hash.substring(0, 4) + "…" + hash.substring(hash.length() - 4);
    }

    public BlockchainService(com.rationchain.model.BeneficiaryRepository repo) {
        this.repo = repo;
    }

    // ── Internal ──────────────────────────────────────────────────────────
    public String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    /** Simulated tx hash for a claim — SHA-256(beneficiaryId + timestamp) */
    public String generateTxHash(long beneficiaryId, long epochMillis) {
        return "0x" + sha256(beneficiaryId + ":" + epochMillis).substring(0, 40);
    }
}
