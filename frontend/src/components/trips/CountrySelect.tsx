import { useEffect, useMemo, useRef, useState } from 'react'
import { getData } from 'country-list'

const COUNTRIES = getData()
  .map(({ code, name }) => ({
    code: code.toLowerCase(),
    name,
  }))
  .sort((a, b) => a.name.localeCompare(b.name))

export function CountrySelect({
  value,
  onChange,
  id,
}: {
  value: string
  onChange: (code: string) => void
  id?: string
}) {
  const [query, setQuery] = useState('')
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  const selected = useMemo(
    () => COUNTRIES.find((country) => country.code === value.toLowerCase()),
    [value],
  )

  const results = useMemo(() => {
    const search = query.trim().toLowerCase()

    return COUNTRIES.filter(
      (country) =>
        !search ||
        country.name.toLowerCase().includes(search) ||
        country.code.includes(search),
    ).slice(0, 100)
  }, [query])

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false)
      }
    }

    document.addEventListener('pointerdown', handlePointerDown)
    return () => document.removeEventListener('pointerdown', handlePointerDown)
  }, [])

  function selectCountry(code: string) {
    onChange(code)
    setQuery('')
    setIsOpen(false)
  }

  return (
    <div ref={containerRef} className="relative">
      <div className="flex min-h-11 flex-wrap gap-2 rounded-xl border border-border bg-surface px-3 py-2">
        <input
          id={id}
          value={isOpen ? query : selected?.name ?? ''}
          onFocus={() => {
            setIsOpen(true)
            setQuery('')
          }}
          onChange={(event) => {
            setQuery(event.target.value)
            setIsOpen(true)
          }}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              setIsOpen(false)
              setQuery('')
            }
            if (event.key === 'Enter' && results.length > 0) {
              event.preventDefault()
              selectCountry(results[0].code)
            }
          }}
          placeholder="Search countries…"
          aria-label="Search countries"
          aria-expanded={isOpen}
          role="combobox"
          aria-autocomplete="list"
          className="min-w-32 flex-1 bg-transparent text-sm outline-none"
        />
      </div>

      {isOpen && (
        <ul
          role="listbox"
          aria-label="Available countries"
          className="absolute z-20 mt-1 max-h-56 w-full overflow-y-auto rounded-xl border border-border bg-surface-raised p-1 shadow-xl"
        >
          {results.length === 0 ? (
            <li className="px-3 py-2 text-sm text-muted">
              No matching countries
            </li>
          ) : (
            results.map((country) => (
              <li key={country.code}>
                <button
                  type="button"
                  role="option"
                  aria-selected={country.code === value.toLowerCase()}
                  onClick={() => selectCountry(country.code)}
                  className="w-full rounded-lg px-3 py-2 text-left text-sm hover:bg-surface-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
                >
                  {country.name}
                  <span className="ml-2 text-xs uppercase text-muted">
                    {country.code}
                  </span>
                </button>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  )
}
