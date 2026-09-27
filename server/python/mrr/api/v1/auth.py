"""Authentication endpoints: register, login, logout.

HTTP boundary only — all work happens in the service layer. Passwords are
never returned or logged; session tokens exist only in the login response.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from mrr.api.deps import (
    get_account_service,
    get_authentication_service,
    get_bearer_credentials,
    get_current_account,
)
from mrr.repositories.accounts import Account
from mrr.services.accounts import (
    AccountAlreadyExistsError,
    AccountService,
    AccountValidationError,
)
from mrr.services.authentication import AuthenticationError, AuthenticationService

router = APIRouter(prefix="/auth", tags=["auth"])

_AUTH_HEADERS = {"WWW-Authenticate": "Bearer"}


class RegisterRequest(BaseModel):
    username: str = Field(min_length=1, max_length=64)
    password: str = Field(min_length=1, max_length=128)


class AccountResponse(BaseModel):
    account_id: int
    username: str


class LoginRequest(BaseModel):
    username: str
    password: str


class LoginResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: int


@router.post("/register", status_code=201, response_model=AccountResponse)
def register(
    payload: RegisterRequest,
    service: AccountService = Depends(get_account_service),
) -> AccountResponse:
    try:
        account = service.register(username=payload.username, password=payload.password)
    except AccountAlreadyExistsError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc
    except AccountValidationError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return AccountResponse(account_id=account.id, username=account.username)


@router.post("/login", response_model=LoginResponse)
def login(
    payload: LoginRequest,
    service: AuthenticationService = Depends(get_authentication_service),
) -> LoginResponse:
    try:
        token, expires_in = service.login(
            username=payload.username, password=payload.password
        )
    except AuthenticationError as exc:
        # Identical message/401 for unknown user and wrong password.
        raise HTTPException(
            status_code=401, detail=str(exc), headers=_AUTH_HEADERS
        ) from exc
    return LoginResponse(access_token=token, expires_in=expires_in)


@router.post("/logout", status_code=204)
def logout(
    credentials=Depends(get_bearer_credentials),
    _account: Account = Depends(get_current_account),
    service: AuthenticationService = Depends(get_authentication_service),
) -> None:
    # get_current_account already rejected invalid/expired tokens with 401.
    service.logout(credentials.credentials)
