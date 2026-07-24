package crypto_test

import (
	"bytes"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/crypto"
)

func TestAESGCMEncryptor_EncryptDecrypt(t *testing.T) {
	masterKey := string(bytes.Repeat([]byte("a"), 32))
	encryptor, err := crypto.NewAESGCMEncryptor(masterKey)
	if err != nil {
		t.Fatalf("failed to create encryptor: %v", err)
	}

	plaintext := []byte("secret_api_token_12345")

	ciphertext, err := encryptor.Encrypt(plaintext)
	if err != nil {
		t.Fatalf("encryption failed: %v", err)
	}

	if ciphertext == "" {
		t.Fatal("expected non-empty ciphertext string")
	}

	decrypted, err := encryptor.Decrypt(ciphertext)
	if err != nil {
		t.Fatalf("decryption failed: %v", err)
	}

	if string(decrypted) != string(plaintext) {
		t.Errorf("decrypted text mismatch: got %q, want %q", string(decrypted), string(plaintext))
	}
}

func TestAESGCMEncryptor_InvalidKeyLength(t *testing.T) {
	invalidKey := "short_key"
	_, err := crypto.NewAESGCMEncryptor(invalidKey)
	if err == nil {
		t.Error("expected error when initializing with invalid key length, got nil")
	}
}

func TestAESGCMEncryptor_InvalidCiphertextFormat(t *testing.T) {
	masterKey := string(bytes.Repeat([]byte("a"), 32))
	encryptor, _ := crypto.NewAESGCMEncryptor(masterKey)

	_, err := encryptor.Decrypt("invalid_base64_payload!!!")
	if err == nil {
		t.Error("expected error for invalid base64 ciphertext, got nil")
	}
}

func TestAESGCMEncryptor_WrongKeyDecryption(t *testing.T) {
	key1 := string(bytes.Repeat([]byte("a"), 32))
	key2 := string(bytes.Repeat([]byte("b"), 32))

	enc1, _ := crypto.NewAESGCMEncryptor(key1)
	enc2, _ := crypto.NewAESGCMEncryptor(key2)

	ciphertext, _ := enc1.Encrypt([]byte("sensitive_data"))

	_, err := enc2.Decrypt(ciphertext)
	if err == nil {
		t.Error("expected error when decrypting with wrong key, got nil")
	}
}
