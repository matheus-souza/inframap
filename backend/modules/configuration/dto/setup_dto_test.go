package dto_test

import (
	"testing"

	"github.com/matheussouza/inframap/modules/configuration/dto"
)

func TestOnboardRequest_Validate(t *testing.T) {
	tests := []struct {
		name    string
		req     dto.OnboardRequest
		wantErr bool
	}{
		{
			name: "valid request with strong passphrase",
			req: dto.OnboardRequest{
				AdminUsername: "admin",
				AdminEmail:    "admin@example.com",
				AdminFullName: "System Admin",
				AdminPassword: "correct-horse-battery-staple-passphrase",
			},
			wantErr: false,
		},
		{
			name: "short password",
			req: dto.OnboardRequest{
				AdminUsername: "admin",
				AdminEmail:    "admin@example.com",
				AdminFullName: "System Admin",
				AdminPassword: "short",
			},
			wantErr: true,
		},
		{
			name: "weak password",
			req: dto.OnboardRequest{
				AdminUsername: "admin",
				AdminEmail:    "admin@example.com",
				AdminFullName: "System Admin",
				AdminPassword: "password123456",
			},
			wantErr: true,
		},
		{
			name: "invalid email",
			req: dto.OnboardRequest{
				AdminUsername: "admin",
				AdminEmail:    "invalid-email",
				AdminFullName: "System Admin",
				AdminPassword: "correct-horse-battery-staple-passphrase",
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			errs := tt.req.Validate()
			if (len(errs) > 0) != tt.wantErr {
				t.Errorf("Validate() error count = %d, wantErr %v", len(errs), tt.wantErr)
			}
		})
	}
}
