// Package crypto provides AES-256-GCM secret encryption and decryption services.
package crypto

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"strings"
)

var (
	// ErrInvalidKeyLength indicates that the provided master key is not 32 bytes.
	ErrInvalidKeyLength = errors.New("master key must be exactly 32 bytes for AES-256")

	// ErrInvalidCiphertext indicates invalid base64 encoding or ciphertext format.
	ErrInvalidCiphertext = errors.New("invalid ciphertext format or payload")
)

// Encryptor defines the contract for secret encryption and decryption.
type Encryptor interface {
	Encrypt(plaintext []byte) (string, error)
	Decrypt(ciphertext string) ([]byte, error)
}

// AESGCMEncryptor implements Encryptor using AES-256-GCM mode.
type AESGCMEncryptor struct {
	key []byte
}

// NewAESGCMEncryptor creates a new AESGCMEncryptor with the given 32-byte master key.
func NewAESGCMEncryptor(masterKey string) (*AESGCMEncryptor, error) {
	key := []byte(masterKey)
	if len(key) != 32 {
		return nil, ErrInvalidKeyLength
	}
	return &AESGCMEncryptor{key: key}, nil
}

// Encrypt encrypts plaintext using AES-256-GCM and returns a base64 string prefixed with v1:.
func (e *AESGCMEncryptor) Encrypt(plaintext []byte) (string, error) {
	block, err := aes.NewCipher(e.key)
	if err != nil {
		return "", fmt.Errorf("failed to create cipher block: %w", err)
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", fmt.Errorf("failed to create GCM mode: %w", err)
	}

	nonce := make([]byte, gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return "", fmt.Errorf("failed to generate nonce: %w", err)
	}

	ciphertext := gcm.Seal(nonce, nonce, plaintext, nil)
	encoded := base64.StdEncoding.EncodeToString(ciphertext)
	return "v1:" + encoded, nil
}

// Decrypt decrypts a v1: prefixed base64 string produced by Encrypt.
func (e *AESGCMEncryptor) Decrypt(ciphertext string) ([]byte, error) {
	if !strings.HasPrefix(ciphertext, "v1:") {
		return nil, ErrInvalidCiphertext
	}
	raw := strings.TrimPrefix(ciphertext, "v1:")
	data, err := base64.StdEncoding.DecodeString(raw)
	if err != nil {
		return nil, ErrInvalidCiphertext
	}

	block, err := aes.NewCipher(e.key)
	if err != nil {
		return nil, fmt.Errorf("failed to create cipher block: %w", err)
	}

	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("failed to create GCM mode: %w", err)
	}

	nonceSize := gcm.NonceSize()
	if len(data) < nonceSize {
		return nil, ErrInvalidCiphertext
	}

	nonce, actualCiphertext := data[:nonceSize], data[nonceSize:]
	plaintext, err := gcm.Open(nil, nonce, actualCiphertext, nil)
	if err != nil {
		return nil, fmt.Errorf("decryption failed: %w", err)
	}

	return plaintext, nil
}
