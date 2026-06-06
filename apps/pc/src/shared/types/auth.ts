export interface LoginCommand {
  username: string;
  password: string;
}

export interface CurrentUserDto {
  username: string;
  displayName: string;
  roles: string[];
}
