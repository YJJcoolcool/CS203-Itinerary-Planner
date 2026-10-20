import { useEffect, useMemo, useRef, useState } from 'react'
import { getData } from 'country-list'

const COUNTRIES = getData()
  .map(({ code, name }) => ({
    code: code.toLowerCase(),
    name,
  }))
  .sort((a, b) => a.name.localeCompare(b.name))

export function CountryMultiSelect({
  value,
  onChange,
  disabled = false,
}: {
  value: string[]
  onChange: (countries: string[]) => void
  disabled?: boolean
}) {
  const [query, setQuery] = useState('')
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  const selected = useMemo(
    () => new Set(value.map((code) => code.toLowerCase())),
    [value],
  )

  const results = useMemo(() => {
    const search = query.trim().toLowerCase()

    return COUNTRIES.filter(
      (country) =>
        !selected.has(country.code) &&
        (!search ||
          country.name.toLowerCase().includes(search) ||
          country.code.includes(search)),
    ).slice(0, 100)
  }, [query, selected])

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

  function addCountry(code: string) {
    if (selected.has(code)) return
    onChange([...value, code])
    setQuery('')
    setIsOpen(true)
  }

  function removeCountry(code: string) {
    onChange(value.filter((item) => item.toLowerCase() !== code))
  }

  return (
    <div ref={containerRef} className="relative">
      <div className="flex min-h-11 flex-wrap gap-2 rounded-xl border border-border bg-surface px-3 py-2">
        {value.map((code) => {
          const country = COUNTRIES.find((item) => item.code === code.toLowerCase())

          return (
            <span
              key={code}
              className="inline-flex items-center gap-1 rounded-full border border-border bg-surface-subtle px-2.5 py-1 text-sm"
            >
              {country?.name ?? code.toUpperCase()}
              <button
                type="button"
                disabled={disabled}
                onClick={() => removeCountry(code.toLowerCase())}
                aria-label={`Remove ${country?.name ?? code}`}
                className="ml-1 text-muted hover:text-foreground disabled:opacity-50"
              >
                ×
              </button>
            </span>
          )
        })}

        <input
          value={query}
          disabled={disabled}
          onFocus={() => setIsOpen(true)}
          onChange={(event) => {
            setQuery(event.target.value)
            setIsOpen(true)
          }}
          onKeyDown={(event) => {
            if (event.key === 'Escape') setIsOpen(false)
            if (event.key === 'Enter' && results.length > 0) {
              event.preventDefault()
              addCountry(results[0].code)
            }
          }}
          placeholder={value.length ? 'Add another country…' : 'Search countries…'}
          aria-label="Search countries"
          aria-expanded={isOpen}
          role="combobox"
          aria-autocomplete="list"
          className="min-w-32 flex-1 bg-transparent text-sm outline-none disabled:opacity-50"
        />
      </div>

      {isOpen && !disabled && (
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
                  aria-selected={false}
                  onClick={() => addCountry(country.code)}
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
