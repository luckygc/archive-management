import { request } from "./client";
import type { CurrentUserDto, LoginCommand } from "../types/auth";

export function login(command: LoginCommand) {
  const body = new URLSearchParams();
  body.set("username", command.username);
  body.set("password", command.password);
  body.set("powToken", command.powToken);

  return request<CurrentUserDto>("/api/auth/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body,
  });
}

export function getCurrentUser() {
  return request<CurrentUserDto>("/api/auth/me");
}

export function logout() {
  return request<void>("/api/auth/logout", {
    method: "POST",
  });
}
