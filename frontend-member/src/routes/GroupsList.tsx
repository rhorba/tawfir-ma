import { Link } from 'react-router-dom'

export default function GroupsList() {
  // Wired to GET /api/v1/groups in Epic 2 (story 2.4)
  const groups: { id: string; name: string; status: string }[] = []

  return (
    <main className="mx-auto max-w-2xl px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-semibold text-(--color-text)">My Groups</h1>
        <Link
          to="/groups/new"
          className="rounded-md bg-(--color-primary) px-3 py-2 text-sm text-white hover:bg-(--color-primary-dark)"
        >
          Create group
        </Link>
      </div>

      {groups.length === 0 ? (
        <p className="text-(--color-text-muted)">No groups yet — create or join one.</p>
      ) : (
        <ul className="space-y-2">
          {groups.map((group) => (
            <li key={group.id}>
              <Link
                to={`/groups/${group.id}`}
                className="block rounded-md border border-(--color-border) p-4 hover:border-(--color-primary)"
              >
                <span className="font-medium">{group.name}</span>
                <span className="ml-2 text-sm text-(--color-text-muted)">{group.status}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
