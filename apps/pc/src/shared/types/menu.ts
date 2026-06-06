export interface MenuNode {
  id: string;
  title: string;
  path?: string;
  icon?: string;
  children?: MenuNode[];
}
