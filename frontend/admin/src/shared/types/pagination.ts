export interface CollectionResponse<T> {
    items: T[];
}

export interface CursorPageResponse<T> {
    items: T[];
    self?: string;
    prev?: string;
    next?: string;
    first?: string;
    total?: number;
}
