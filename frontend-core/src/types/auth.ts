export interface LoginCommand {
    username: string;
    password: string;
    powToken: string;
}

export interface CurrentUserDto {
    username: string;
    displayName: string;
    roles: string[];
}
