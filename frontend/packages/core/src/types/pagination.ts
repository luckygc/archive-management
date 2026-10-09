export interface CollectionResponse<T> {
    items: T[];
}

export interface CursorPageResponse<T> {
    items: T[];
    self?: string | null;
    prev?: string | null;
    next?: string | null;
    first?: string | null;
    total?: number;
}
